String getReleaseVersion(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'dr'
    String release       = stageParams.release 

    if (release == true ){
        return sh(
            script: """
                kubectl get dr -n ${namespace} ${resourceName}-svc -o=jsonpath="{.spec.subsets[?(@.name=='release')].labels.app\\.kubernetes\\.io/version}"
            """,
            returnStdout: true
        ).trim()
    } else {
        return sh(
            script: """
                kubectl get dr -n ${namespace} ${resourceName}-svc -o=jsonpath="{.spec.subsets[?(@.name=='candidate')].labels.app\\.kubernetes\\.io/version}"
            """,
            returnStdout: true
        ).trim()
    }
}


String getPatchJsonResponseDeployment(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName

    def jsonResult = sh( 
        script: """#!/bin/bash
        kubectl get deployment -n ${namespace} ${resourceName} -o=json | 
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
            kubectl get hpa ${resourceName} -n ${namespace} -o=json | \
            jq '{
                "spec": (.spec | {
                    maxReplicas, 
                    minReplicas, 
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
                })
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

void revertResource(Map params = [:]) {
    String namespace = params.namespace ?: 'default'
    String resourceName = params.resourceName
    String resourceType = params.resourceType ?: 'deployment'
    String jobName = params.jobName
    String backupPrefix = params.backupPrefix ?: 'backup'

    echo "=== Starting revert for ${resourceType}: ${resourceName} ==="
    
    // Construiește pattern-ul pentru backup
    def artifactPattern = "${backupPrefix}-${resourceName}-*.json"
    
    // Copiază artefactele din build-ul anterior
    copyArtifacts(
        projectName: jobName,
        selector: lastSuccessful(),
        filter: artifactPattern
    )
    
    // Găsește cel mai recent backup
    def backupFile = sh(
        script: "ls -1 ${artifactPattern} | head -1",
        returnStdout: true
    ).trim()
    
    if (backupFile) {
        echo "Found backup file: ${backupFile}"
        
        // Citește conținutul backup-ului
        def revertPatch = readFile(backupFile)
        
        // Creează fișierul de patch pentru revert
        def revertFileName = "revert-${resourceName}.json"
        writeFile file: revertFileName, text: revertPatch
        
        // Aplică patch-ul
        patchUpdateFileJSON([
            namespace: namespace,
            resourceName: resourceName,
            resourceType: resourceType,
            patchFile: revertFileName
        ])
        
        echo "Successfully reverted ${resourceType}: ${resourceName}"
    } else {
        error "No backup files found for ${resourceType}: ${resourceName}"
    }
}

String backupResource(Map params = [:]) {
    String namespace = params.namespace ?: 'default'
    String resourceName = params.resourceName
    String resourceType = params.resourceType ?: 'deployment'
    String backupPrefix = params.backupPrefix ?: 'backup'
    String releaseVersion = params.releaseVersion
    String serviceName = params.serviceName

    echo "=== Starting backup for ${resourceType}: ${resourceName} ==="
    
    def timestamp = new Date().format('yyyyMMdd-HHmmss')
    def backupFileName = "${backupPrefix}-${resourceName}-${timestamp}.json"
    
    def jsonResponse
    
    // Obține JSON response în funcție de tipul resursei
    if (resourceType == 'deployment') {
        jsonResponse = getPatchJsonResponseDeployment([
            namespace: namespace,
            resourceName: serviceName.replace("-svc","-dep"),
            releaseVersion: releaseVersion
        ])
    } else if (resourceType == 'hpa') {
        jsonResponse = getHPAPatchJsonResponse([
            namespace: namespace,
            resourceName: resourceName
        ])
    } else {
        error "Unsupported resource type: ${resourceType}"
    }
    
    if (!jsonResponse?.trim()) {
        error "Failed to get JSON response for ${resourceType}: ${resourceName}"
    }
    
    return [fileName: backupFileName, content: jsonResponse]
}