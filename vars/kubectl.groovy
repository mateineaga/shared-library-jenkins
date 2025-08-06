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

    return sh( 
        script: """
        kubectl get deployment ${resourceName} -n ${namespace} -o=jsonpath='{.spec.template.spec.containers[0].resources}' | jq '.'
        """,
        returnStdout: true
    ).trim()
}

String checkResourcesHPA(Map stageParams = [:]) {
    String namespace    = stageParams.namespace    ?: 'default'
    String resourceName = stageParams.resourceName

    return sh( 
        script: """
        kubectl get hpa ${resourceName} -n ${namespace} -o=jsonpath='{.spec}' | jq '.'
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

