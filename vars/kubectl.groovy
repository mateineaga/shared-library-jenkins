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


String getPatchJsonResponseDeployment(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String releaseVersion = stageParams.releaseVersion 

    def jsonResult = sh( 
        script: """#!/bin/bash
        kubectl get deployment -n ${namespace} ${resourceName}-${releaseVersion} -o=json | 
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

    return jsonResult
}

String getHPAPatchJsonResponse(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    
    return sh( 
        script: """
            kubectl get hpa ${resourceName} -n ${namespace} -o=jsonpath='{.spec}' | \
            jq '{
                spec: {
                    maxReplicas: .maxReplicas, 
                    minReplicas: .minReplicas, 
                    metrics: [{
                        type: .metrics[0].type, 
                        resource: {
                            name: .metrics[0].resource.name, 
                            target: {
                                type: .metrics[0].resource.target.type, 
                                averageUtilization: .metrics[0].resource.target.averageUtilization
                            }
                        }
                    }]
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

String filterResourcesByIdentifier(Map params = [:]) {
    String resources = params.resources
    String identifier = params.identifier
    
    return resources.split('\n')
        .findAll { it.contains(identifier) }
        .join('\n')
}


String checkResourcesDeployment(Map stageParams = [:]) {
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

String checkResourcesHPA(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'deployment'

    return sh( 
        script: """
        kubectl get ${resourceType} ${resourceName} -n ${namespace} -o=jsonpath='{.spec}' | jq '.'
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

String extractResourcesFromBackupDeployment(Map stageParams = [:]) {
    String backupFile = stageParams.backupFile
    String resourceName = stageParams.resourceName
    
    return sh(
        script: """#!/bin/bash
        cat ${backupFile} | 
            jq '{
                "spec": {
                    "template": {
                        "spec": {
                            "containers": [{
                                "image": .spec.template.spec.containers[0].image,
                                "name": .spec.template.spec.containers[0].name,
                                "resources": .spec.template.spec.containers[0].resources
                            }]
                        }
                    }
                }
            }'
        """,
        returnStdout: true
    ).trim()
}