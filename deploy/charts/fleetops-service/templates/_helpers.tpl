{{/* The service's name: the name of every resource this release creates. */}}
{{- define "fleetops-service.name" -}}
{{ required "values: name is required" .Values.name }}
{{- end }}

{{/* The labels a Service and a Deployment select pods by. They never change. */}}
{{- define "fleetops-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "fleetops-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/* Labels on every resource. */}}
{{- define "fleetops-service.labels" -}}
{{ include "fleetops-service.selectorLabels" . }}
app.kubernetes.io/version: {{ .Values.image.tag | quote }}
app.kubernetes.io/part-of: fleetops
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end }}
