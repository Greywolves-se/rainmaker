package se.rainmaker.infra;

import java.util.List;
import java.util.Map;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ce.CfnAnomalyMonitor;
import software.amazon.awscdk.services.ce.CfnAnomalySubscription;
import software.amazon.awscdk.services.iam.AddToResourcePolicyResult;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.sns.Topic;
import software.constructs.Construct;

/**
 * Cost guard-rail for the dev account (#161): Cost Anomaly Detection watches the cost of every AWS
 * service, Bedrock included, and alerts an SNS topic at once when an anomaly reaches the threshold.
 *
 * <p>No e-mail address is in the code (the repository is public). People subscribe themselves to
 * the topic {@code rainmaker-cost-alerts} once, in the SNS console, and confirm the e-mail. All of
 * this is free: Cost Anomaly Detection costs nothing, and SNS e-mails stay within the free tier.
 * The topic is not encrypted with a customer-managed KMS key, which would cost about $1 a month.
 *
 * <p>A budget for Bedrock alone cannot be filtered until Bedrock has billing history; the
 * account-wide budget ($50 a month) already covers it until then.
 */
public class CostGuardrailsStack extends Stack {

  private static final String COST_ALERTS_SERVICE = "costalerts.amazonaws.com";

  /** Creates the stack. */
  public CostGuardrailsStack(
      Construct scope, String id, StackProps props, CostAlertSettings settings) {
    super(scope, id, props);

    Topic topic =
        Topic.Builder.create(this, "CostAlertsTopic")
            .topicName("rainmaker-cost-alerts")
            .displayName("Rainmaker cost alerts")
            .build();
    // Only Cost Anomaly Detection, and only for this account, may publish (Cost Management guide).
    AddToResourcePolicyResult publish =
        topic.addToResourcePolicy(
            PolicyStatement.Builder.create()
                .sid("AllowCostAnomalyDetection")
                .principals(List.of(new ServicePrincipal(COST_ALERTS_SERVICE)))
                .actions(List.of("sns:Publish"))
                .resources(List.of(topic.getTopicArn()))
                .conditions(Map.of("StringEquals", Map.of("aws:SourceAccount", getAccount())))
                .build());

    String monitorArn = settings.existingMonitorArn();
    if (monitorArn == null) {
      monitorArn =
          CfnAnomalyMonitor.Builder.create(this, "ServicesMonitor")
              .monitorName("rainmaker-aws-services")
              .monitorType("DIMENSIONAL")
              .monitorDimension("SERVICE")
              .build()
              .getRef();
    }

    CfnAnomalySubscription subscription =
        CfnAnomalySubscription.Builder.create(this, "AnomalySubscription")
            .subscriptionName("rainmaker-cost-anomalies")
            // IMMEDIATE alerts can only go to SNS; e-mail is limited to daily or weekly summaries.
            .frequency("IMMEDIATE")
            .monitorArnList(List.of(monitorArn))
            .subscribers(
                List.of(
                    CfnAnomalySubscription.SubscriberProperty.builder()
                        .type("SNS")
                        .address(topic.getTopicArn())
                        .build()))
            .thresholdExpression(
                "{\"Dimensions\":{\"Key\":\"ANOMALY_TOTAL_IMPACT_ABSOLUTE\","
                    + "\"MatchOptions\":[\"GREATER_THAN_OR_EQUAL\"],\"Values\":[\""
                    + settings.thresholdUsd()
                    + "\"]}}")
            .build();
    // Cost Anomaly Detection checks that it may publish when the subscription is created.
    subscription.getNode().addDependency(publish.getPolicyDependable());
  }
}
