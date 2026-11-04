package se.rainmaker.g3publisherapp;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The module rules from CLAUDE.md ("Architecture rules") for the services this app assembles. */
class ModuleBoundaryTest {

  private static final List<String> SERVICES =
      List.of("se.rainmaker.publishercontrol", "se.rainmaker.advertisermarketplace");

  /**
   * Each service's own transaction manager ({@code <Module>DatabaseConfiguration
   * .TRANSACTION_MANAGER}). A service that isn't listed has no database.
   */
  private static final Map<String, String> TRANSACTION_MANAGERS =
      Map.of(
          "se.rainmaker.publishercontrol", "publisherControlTransactionManager",
          "se.rainmaker.advertisermarketplace", "advertiserMarketplaceTransactionManager");

  private static final String SPRING_TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";
  private static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("se.rainmaker");

  @Test
  void otherCodeUsesOnlyTheApiPackageOfService() {
    for (String service : SERVICES) {
      noClasses()
          .that()
          .resideOutsideOfPackage(service + "..")
          .should()
          .dependOnClassesThat(
              resideInAPackage(service + "..").and(not(resideInAPackage(service + ".api.."))))
          .because("other modules may only use a service's api package")
          .check(CLASSES);
    }
  }

  @Test
  void servicesNeverDependOnApps() {
    noClasses()
        .that()
        .resideInAnyPackage(SERVICES.stream().map(service -> service + "..").toArray(String[]::new))
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "se.rainmaker.g1readerapp..",
            "se.rainmaker.g2contentapp..",
            "se.rainmaker.g3publisherapp..")
        .because("apps assemble services, never the other way")
        // The services are still empty (#155), so there may be nothing to check yet.
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  /**
   * The app holds several modules' databases, so a plain {@code @Transactional} can't choose a
   * transaction manager and fails at runtime, the first time it is called (#170). A module also
   * never writes in another module's transaction.
   */
  @Test
  void servicesNameTheirOwnTransactionManager() {
    for (String service : SERVICES) {
      String manager = TRANSACTION_MANAGERS.getOrDefault(service, "");
      classes()
          .that()
          .resideInAPackage(service + "..")
          .and()
          .areAnnotatedWith(SPRING_TRANSACTIONAL)
          .should(nameTransactionManager(manager))
          // A service may have no @Transactional yet, so there may be nothing to check.
          .allowEmptyShould(true)
          .check(CLASSES);
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage(service + "..")
          .and()
          .areAnnotatedWith(SPRING_TRANSACTIONAL)
          .should(nameTransactionManager(manager))
          .allowEmptyShould(true)
          .check(CLASSES);
    }
  }

  /** {@code jakarta.transaction.Transactional} can't name a transaction manager at all (#170). */
  @Test
  void servicesNeverUseJakartaTransactional() {
    String[] packages = SERVICES.stream().map(service -> service + "..").toArray(String[]::new);
    String reason =
        JAKARTA_TRANSACTIONAL
            + " can't name the module's transaction manager; use Spring's @Transactional";
    noClasses()
        .that()
        .resideInAnyPackage(packages)
        .should()
        .beAnnotatedWith(JAKARTA_TRANSACTIONAL)
        .because(reason)
        .allowEmptyShould(true)
        .check(CLASSES);
    noMethods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAnyPackage(packages)
        .should()
        .beAnnotatedWith(JAKARTA_TRANSACTIONAL)
        .because(reason)
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  /** Spring's {@code @Transactional} names {@code manager}; an empty one means "no database". */
  private static <T extends HasAnnotations<?> & HasDescription>
      ArchCondition<T> nameTransactionManager(String manager) {
    return new ArchCondition<>("name the module's own transaction manager") {
      @Override
      public void check(T annotated, ConditionEvents events) {
        String named = transactionManager(annotated.getAnnotationOfType(SPRING_TRANSACTIONAL));
        if (manager.isEmpty()) {
          events.add(
              SimpleConditionEvent.violated(
                  annotated,
                  annotated.getDescription()
                      + " is @Transactional, but its module has no database"));
        } else if (!manager.equals(named)) {
          events.add(
              SimpleConditionEvent.violated(
                  annotated,
                  annotated.getDescription()
                      + " must name its module's transaction manager: @Transactional(<Module>"
                      + "DatabaseConfiguration.TRANSACTION_MANAGER), \""
                      + manager
                      + "\", not \""
                      + named
                      + "\""));
        }
      }
    };
  }

  /** {@code value} and {@code transactionManager} are aliases; empty when neither is set. */
  private static String transactionManager(JavaAnnotation<?> transactional) {
    String value = transactional.get("value").map(String.class::cast).orElse("");
    return value.isEmpty()
        ? transactional.get("transactionManager").map(String.class::cast).orElse("")
        : value;
  }
}
