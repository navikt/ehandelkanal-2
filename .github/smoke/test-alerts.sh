#!/usr/bin/env bash
set -euo pipefail

PROMTOOL="${1:-promtool}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -f "$TEMP_DIR/prometheusrule-dev.yaml" "$TEMP_DIR/prometheusrule-prod.yaml" "$TEMP_DIR/alert-rules.test.json"; rmdir "$TEMP_DIR"' EXIT

ruby -ryaml -rjson - "$ROOT" "$TEMP_DIR" <<'RUBY'
root, output = ARGV
annotations = {}
%w[dev prod].each do |environment|
  file = "prometheusrule-#{environment}.yaml"
  resource = YAML.load_file("#{root}/.nais/#{file}")
  abort "Invalid resource: #{file}" unless resource.fetch("apiVersion") == "monitoring.coreos.com/v1" &&
    resource.fetch("kind") == "PrometheusRule" &&
    resource.fetch("metadata").fetch("name") == "ehandelkanal-2" &&
    resource.fetch("metadata").fetch("namespace") == "team-oebs"
  resource.fetch("spec").fetch("groups").each do |group|
    group.fetch("rules").each do |rule|
      labels = rule.fetch("labels")
      expected = {
        "namespace" => "team-oebs", "app" => "ehandelkanal-2",
        "env" => "#{environment}-fss", "alert_type" => "custom"
      }
      abort "Invalid routing: #{rule.fetch('alert')}" unless expected.all? { |key, value| labels[key] == value }
      abort "Invalid severity" unless %w[critical warning info].include?(labels.fetch("severity"))
      %w[summary consequence action runbook_url].each { |key| rule.fetch("annotations").fetch(key) }
      expression = rule.fetch("expr")
      abort "Missing cluster filter" unless expression.include?("cluster=\"#{environment}-fss\"")
      abort "Missing namespace filter" unless expression.include?('namespace="team-oebs"')
      abort "Missing application filter" unless expression.include?('container="ehandelkanal-2"') ||
        expression.include?('deployment="ehandelkanal-2"')
      annotations[[rule.fetch("alert"), "#{environment}-fss"]] = rule.fetch("annotations")
    end
  end
  File.write("#{output}/#{file}", YAML.dump(resource.fetch("spec")))
end
tests = YAML.safe_load(File.read("#{root}/.nais/tests/alert-rules.test.yaml"), aliases: true)
tests.fetch("tests").each do |test|
  test.fetch("alert_rule_test").each do |expectation|
    expectation.fetch("exp_alerts").each do |alert|
      alert["exp_annotations"] = annotations.fetch([expectation.fetch("alertname"), alert.fetch("exp_labels").fetch("env")])
    end
  end
end
File.write("#{output}/alert-rules.test.json", JSON.generate(tests))
RUBY

"$PROMTOOL" check rules "$TEMP_DIR/prometheusrule-dev.yaml" "$TEMP_DIR/prometheusrule-prod.yaml"
"$PROMTOOL" test rules "$TEMP_DIR/alert-rules.test.json"
