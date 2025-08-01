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
    String resourceType = stageParams.resourceType ?: 'deployment'
    String releaseVersion = stageParams.releaseVersion 

    return sh( 
        script: """#!/bin/bash
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
        """,
        returnStdout: true
    ).trim()
}

String getResources(Map params = [:]) {
    String resources = params.resources
    String namespace = params.namespace ?: 'default'
    
    return sh( 
        script: """
            kubectl get ${resources} -n ${namespace} -o=jsonpath="{range .items[*]}{.metadata.name}{\\"\\n\\"}"
        """,
        returnStdout: true
    ).trim()
}

String filterResourcesByVersion(Map params = [:]) {
    String resources = params.resources
    String version = params.version
    
    return resources.split('\n')
        .findAll { it.contains(version) }
        .join('\n')
}

String checkResources(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'deployment'

    return sh( 
        script: """
        kubectl get ${resourceType} ${resourceName} -n ${namespace} -o=jsonpath='{.spec.template.spec.containers[0].resources}' | jq '.'
        """,
        returnStdout: true
    ).trim()
}

String getSpecificResource(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'hpa'

    return sh( 
        script: """
        kubectl get ${resourceType} ${resourceName} -n ${namespace}
        """,
        returnStdout: true
    ).trim()
}

void patchUpdateFileJSON(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String options      = stageParams.options      ?: ''
    String patchFile    = stageParams.patchFile
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'deployment'

    options             = options.replaceAll('(\\r\\n|\\n|\\s\\s)+', ' ')
    sh """
        kubectl patch \
            ${resourceType} \
            ${resourceName} \
            -n ${namespace} \
            --patch-file ${patchFile} \
            ${options}
    """
}
