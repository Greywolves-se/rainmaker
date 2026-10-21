package se.rainmaker.g1readerapp;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The module rules from CLAUDE.md ("Architecture rules") for the services this app assembles. */
class ModuleBoundaryTest {

  private static final List<String> SERVICES =
      List.of("se.rainmaker.widgetdelivery", "se.rainmaker.trackingclick");

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
}
