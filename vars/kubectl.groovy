String getReleaseVersion(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType ?: 'dr'
    String release     = stageParams.release 

    String serviceName = resourceName == 'bloomreach' ? 'bloomreach-authoring' : resourceName

    if (release.toString().toBoolean()){
        return sh(
            script: """
                kubectl get dr -n ${namespace} ${serviceName}-svc -o=jsonpath="{.spec.subsets[?(@.name=='release')].labels.app\\.kubernetes\\.io/version}" | sed 's/\\./\\-/g'
            """,
            returnStdout: true
        ).trim()
    } else {
        return sh(
            script: """
                kubectl get dr -n ${namespace} ${serviceName}-svc -o=jsonpath="{.spec.subsets[?(@.name=='candidate')].labels.app\\.kubernetes\\.io/version}" | sed 's/\\./\\-/g'
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
        def valuesContent = readYaml file: stageParams.valuesFile
        def resources = valuesContent.resources

        if (!resources) {
            error "No resources defined in values file"
        }

        def containerName
        if (stageParams.deployment.startsWith("bloomreach-")) {
            containerName = "bloomreach"
            if (stageParams.deployment.contains("authoring")) {
                resources = valuesContent.authoring.resources
            } else {
                resources = valuesContent.delivery.resources
        } else {
            containerName = stageParams.deployment.replaceAll("-dep-[0-9.-]+\$", "")
            resources = valuesContent.resources
        }
        
        echo "Extracted container name: ${containerName}"

        def jsonString = """
            {
                "spec": {
                    "template": {
                        "spec": {
                            "containers": [{
                                "name": "${containerName}",
                                "image": "${stageParams.imageContainer}",
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
        def resources

        if (stageParams.resourceName?.contains("bloomreach-")) {
            if (stageParams.resourceName.contains("authoring")) {
                resources = valuesContent.authoring.hpa
            } else {
                resources = valuesContent.delivery.hpa
            }
        } else {
            resources = valuesContent.hpa
        }

        if (!resources) {
            error "No HPA resources defined in values file for ${stageParams.resourceName}"
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
    

String get(Map stageParams = [:]) {
    String resources = stageParams.resources
    String namespace = stageParams.namespace ?: 'default'
    String options      = stageParams.options      ?: ''

    options             = options.replaceAll('(\\r\\n|\\n|\\s\\s)+', ' ')

    return sh( 
        script: """
            kubectl get ${resources} -n ${namespace} ${options}
        """,
        returnStdout: true
    ).trim()
}

String filterResourcesByIdentifier(Map params = [:]) {
    String resources = params.resources?.trim()
    String identifier = params.identifier?.replaceAll("\\.", "-")

    if (!resources) {
        echo "No resources to filter!"
        return ""
    }

    def filteredResources = []
    def resourcesList = resources.split('\n')
    
    resourcesList.each { resource ->
        if (resource.trim()) {
            if (resource.contains(identifier)) {
                filteredResources.add(resource.trim())
            }
        }
    }

    def result = filteredResources.join('\n').trim()
    
    return result
}


String checkResources(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName
    String resourceType = stageParams.resourceType
    String options      = stageParams.options      ?: ''

    options             = options.replaceAll('(\\r\\n|\\n|\\s\\s)+', ' ')



    return sh( 
        script: """
        kubectl get ${resourceType} ${resourceName} -n ${namespace} ${options}
        """,
        returnStdout: true
    ).trim()
}


void patchUpdateFile(Map stageParams = [:]) {
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

