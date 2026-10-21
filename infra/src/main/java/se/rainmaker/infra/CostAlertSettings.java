package se.rainmaker.infra;

import java.util.Map;
import software.constructs.Construct;

/**
 * Settings for the cost anomaly alert (#161), from the {@code rainmaker:cost-alerts} context.
 *
 * @param thresholdUsd alert when an anomaly's total impact is at least this many US dollars
 * @param existingMonitorArn an "AWS services" monitor that already exists in the account, or null
 *     to create one. An account can have only one such monitor; pass it with {@code -c
 *     rainmaker:existing-cost-monitor-arn=<arn>} if the console already shows one.
 */
public record CostAlertSettings(int thresholdUsd, String existingMonitorArn) {

  static final String CONTEXT_KEY = "rainmaker:cost-alerts";
  static final String EXISTING_MONITOR_KEY = "rainmaker:existing-cost-monitor-arn";

  /** Checks that the threshold is positive. */
  public CostAlertSettings {
    if (thresholdUsd <= 0) {
      throw new IllegalArgumentException(
          "CDK context \"" + CONTEXT_KEY + "\": thresholdUsd must be at least 1: " + thresholdUsd);
    }
  }

  /** Reads the settings from the context. */
  public static CostAlertSettings fromContext(Construct scope) {
    Map<?, ?> settings = ContextValues.requireObject(scope, CONTEXT_KEY);
    if (!(settings.get("thresholdUsd") instanceof Number threshold)) {
      throw new IllegalArgumentException(
          "CDK context \"" + CONTEXT_KEY + "\": \"thresholdUsd\" must be a number");
    }
    Object existing = scope.getNode().tryGetContext(EXISTING_MONITOR_KEY);
    return new CostAlertSettings(
        threshold.intValue(), existing == null ? null : existing.toString());
  }
}
