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

String getPatchJsonResponse(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'dr'
    String releaseVersion = stageParams.releaseVersion 

    return sh( 
        script: '''#!/bin/bash
        kubectl get ${resourceType} -n ${namespace} ${resourceName}-${releaseVersion} -o=json | 
            jq '{
                "spec": {
                    "template": {
                        "spec": {
                            "containers": [{
                                "image": .spec.template.spec.containers[0].image,
                                "name": .spec.template.spec.containers[0].name,
                                "resources": {   
                                    "limits": {
                                        "cpu": .spec.template.spec.containers[0].resources.limits.cpu,
                                        "ephemeral-storage": .spec.template.spec.containers[0].resources.limits["ephemeral-storage"],
                                        "memory": .spec.template.spec.containers[0].resources.limits.memory
                                    },
                                    "requests": {
                                        "cpu": .spec.template.spec.containers[0].resources.requests.cpu,
                                        "ephemeral-storage": .spec.template.spec.containers[0].resources.requests["ephemeral-storage"],
                                        "memory": .spec.template.spec.containers[0].resources.requests.memory
                                    }
                                }
                            }]
                        }
                    }
                }
            }'
        ''',
        returnStdout: true
    ).trim()
}

// env.JSON_RESPONSE = kubectl.getPatchJsonResponse(
//     namespace: "${SOURCE_NAMESPACE}",
//     resourceName: "${SERVICE_NAME}",
//     resourceType: 'deployment'
//     releaseVersion: "${env.RELEASE_VERSION}"
// )

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