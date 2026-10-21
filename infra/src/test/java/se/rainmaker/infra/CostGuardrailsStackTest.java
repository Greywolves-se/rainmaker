package se.rainmaker.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

/**
 * Cost Anomaly Detection covers Bedrock and every other service in the dev account, and alerts go
 * to an SNS topic, so no e-mail address is ever in the repository (#161).
 */
class CostGuardrailsStackTest {

  private static final String ACCOUNT = "111111111111";

  private static Template synthesize(String existingMonitorArn) {
    App app = App.Builder.create().build();
    StackProps props =
        StackProps.builder()
            .env(Environment.builder().account(ACCOUNT).region("eu-north-1").build())
            .build();
    return Template.fromStack(
        new CostGuardrailsStack(
            app, "CostGuardrails-dev", props, new CostAlertSettings(5, existingMonitorArn)));
  }

  @Test
  void monitorsTheCostOfEveryAwsService() {
    Template template = synthesize(null);

    template.resourceCountIs("AWS::CE::AnomalyMonitor", 1);
    template.hasResourceProperties(
        "AWS::CE::AnomalyMonitor",
        Map.of(
            "MonitorName", "rainmaker-aws-services",
            "MonitorType", "DIMENSIONAL",
            "MonitorDimension", "SERVICE"));
  }

  @Test
  void alertsImmediatelyThroughTheTopicFromFiveDollars() {
    Template template = synthesize(null);

    template.hasResourceProperties(
        "AWS::CE::AnomalySubscription",
        Match.objectLike(
            Map.of(
                "SubscriptionName",
                "rainmaker-cost-anomalies",
                "Frequency",
                "IMMEDIATE",
                "ThresholdExpression",
                "{\"Dimensions\":{\"Key\":\"ANOMALY_TOTAL_IMPACT_ABSOLUTE\","
                    + "\"MatchOptions\":[\"GREATER_THAN_OR_EQUAL\"],\"Values\":[\"5\"]}}",
                "Subscribers",
                List.of(
                    Map.of(
                        "Type",
                        "SNS",
                        "Address",
                        Map.of("Ref", Match.stringLikeRegexp("CostAlertsTopic.*")))))));
  }

  @Test
  void onlyCostAnomalyDetectionInThisAccountMayPublish() {
    Template template = synthesize(null);

    template.hasResourceProperties("AWS::SNS::Topic", Map.of("TopicName", "rainmaker-cost-alerts"));
    template.hasResourceProperties(
        "AWS::SNS::TopicPolicy",
        Map.of(
            "PolicyDocument",
            Map.of(
                "Version",
                "2012-10-17",
                "Statement",
                List.of(
                    Map.of(
                        "Sid",
                        "AllowCostAnomalyDetection",
                        "Effect",
                        "Allow",
                        "Principal",
                        Map.of("Service", "costalerts.amazonaws.com"),
                        "Action",
                        "sns:Publish",
                        "Resource",
                        Map.of("Ref", Match.stringLikeRegexp("CostAlertsTopic.*")),
                        "Condition",
                        Map.of("StringEquals", Map.of("aws:SourceAccount", ACCOUNT)))))));
  }

  @Test
  void containsNoEmailSubscriberOrAddress() {
    String template = synthesize(null).toJSON().toString();

    assertThat(template).doesNotContain("EMAIL").doesNotContain("@");
  }

  @Test
  void usesAnExistingServicesMonitorInsteadOfCreatingOne() {
    String existing = "arn:aws:ce::" + ACCOUNT + ":anomalymonitor/abc-123";

    Template template = synthesize(existing);

    template.resourceCountIs("AWS::CE::AnomalyMonitor", 0);
    template.hasResourceProperties(
        "AWS::CE::AnomalySubscription", Map.of("MonitorArnList", List.of(existing)));
  }

  @Test
  void refusesThresholdOfZeroOrLess() {
    assertThatThrownBy(() -> new CostAlertSettings(0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("thresholdUsd");
  }
}
