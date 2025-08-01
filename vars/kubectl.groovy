String getReleaseVersion(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'dr'

    return sh(
        script: """
            kubectl get ${resourceType} -n ${namespace} ${resourceName} -o=jsonpath="{.spec.subsets[?(@.name=='release')].labels.app\\.kubernetes\\.io/version}" | sed 's/\\./\\-/g'
        """,
        returnStdout: true
    ).trim()
}

void patchUpdateFileJSON(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String options      = stageParams.options      ?: ''
    String patchJSON    = stageParams.patchJSON    ?: ''
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'deployment'

    options             = options.replaceAll('(\\r\\n|\\n|\\s\\s)+', ' ')
    sh """
        kubectl patch \
            ${resourceType} \
            ${resourceName} \
            -n ${namespace} \
            --patch ${patchJSON} \
            ${options}
    """
}

// kubectl.patchUpdateFileJSON(
//     namespace: ${TARGET_NAMESPACE}
//     patchJSON: ${JSON_RESPONSE}
//     resourceName: $deployment
//     resourceType: 'deployment'
// )