String getReleaseVersion(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'dr'
    String release       = stageParams.release 

    if (release.toString().toBoolean()){
        return sh(
            script: """
                kubectl get dr -n ${namespace} ${resourceName}-svc -o=jsonpath="{.spec.subsets[?(@.name=='release')].labels.app\\.kubernetes\\.io/version}" | sed 's/\\./\\-/g'
            """,
            returnStdout: true
        ).trim()
    } else {
        return sh(
            script: """
                kubectl get dr -n ${namespace} ${resourceName}-svc -o=jsonpath="{.spec.subsets[?(@.name=='candidate')].labels.app\\.kubernetes\\.io/version}" | sed 's/\\./\\-/g'
            """,
            returnStdout: true
        ).trim()
    }
}


String getPatchJsonResponseDeployment(Map stageParams = [:]) {
    if (!stageParams.valuesFile) {
        error "valuesFile is required"
    }

    try {
        // Citește values file
        def valuesContent = readYaml file: stageParams.valuesFile
        
        // Preia resursele din values file
        def resources = valuesContent.resources

        if (!resources) {
            error "No resources defined in values file"
        }

        def jsonString = """
            {
                "spec": {
                    "template": {
                        "spec": {
                            "containers": [{
                                "resources": {   
                                    "limits": {
                                        "cpu": "${resources.limits.cpu}",
                                        "memory": "${resources.limits.memory}"
                                    },
                                    "requests": {
                                        "cpu": "${resources.requests.cpu}",
                                        "memory": "${resources.requests.memory}"
                                    }
                                }
                            }]
                        }
                    }
                }
            }
            """


        return jsonString

    } catch (Exception e) {
        error "No patch genereated: ${e.message}"
    }
}

String getHPAPatchJsonResponse(Map stageParams = [:]) {
    if (!stageParams.valuesFile) {
        error "valuesFile is required"
    }

    try {
        def valuesContent = readYaml file: stageParams.valuesFile
        def resources = valuesContent.hpa

        if (!resources) {
            error "No HPA resources defined in values file"
        }

        def jsonString = """
            {
                "spec": {
                    "maxReplicas": ${resources.pods.max},
                    "minReplicas": ${resources.pods.min},
                    "metrics": [{
                        "type": "Resource",
                        "resource": {
                            "name": "cpu",
                            "target": {
                                "type": "Utilization",
                                "averageUtilization": ${resources.metrics.cpu}
                            }
                        }
                    }]
                }
            }
        """

        return jsonString.trim()

    } catch (Exception e) {
        error "No patch generated: ${e.message}"
    }
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
    String identifier = params.identifier?.replaceAll("\\.", "-")

    if (!resources) {
        echo "No resources to filter!"
        return ""
    }

    def filteredResources = []
    def resourcesList = resources.split('\n')
    
    resourcesList.each { resource ->
        if (resource.trim()) {
            // echo "Resource after trim: [${resource.trim()}]"
            if (resource.contains(identifier)) {
                // echo "Found match!"
                filteredResources.add(resource.trim())
            }
        }
    }

    def result = filteredResources.join('\n')
    
    return result
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