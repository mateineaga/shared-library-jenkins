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
        def resources
        def containerName

        if (stageParams.deployment.startsWith("bloomreach-")) {
            containerName = "bloomreach"
            // Determină dacă este authoring sau delivery
            if (stageParams.deployment.contains("authoring")) {
                resources = valuesContent.authoring.resources
                if (!resources) {
                    error "No resources defined in values file for authoring"
                }
            } else {
                resources = valuesContent.delivery.resources
                
                if (!resources) {
                    echo "No resources defined in values file for delivery - skipping resources patch"
                    return null
                }
            }

            def resourcesJson = [:]
            
            if (resources.limits) {
                resourcesJson.limits = [:]
                if (resources.limits.memory) resourcesJson.limits.memory = resources.limits.memory
                if (resources.limits.cpu) resourcesJson.limits.cpu = resources.limits.cpu
                if (resources.limits.ephemeralStorage) resourcesJson.limits.ephemeralStorage = resources.limits.ephemeralStorage
            }
            
            if (resources.requests) {
                resourcesJson.requests = [:]
                if (resources.requests.memory) resourcesJson.requests.memory = resources.requests.memory
                if (resources.requests.cpu) resourcesJson.requests.cpu = resources.requests.cpu
            }

            def jsonString = """
                {
                    "spec": {
                        "template": {
                            "spec": {
                                "containers": [{
                                    "name": "${containerName}",
                                    "image": "${stageParams.imageContainer}",
                                    "resources": ${groovy.json.JsonOutput.toJson(resourcesJson)}
                                }]
                            }
                        }
                    }
                }
                """

            return jsonString

        } else {
            containerName = stageParams.deployment.replaceAll("-dep-[0-9.-]+\$", "")
            resources = valuesContent.resources
            if (!resources) {
                error "No resources defined in values file for ${stageParams.deployment}"
            }

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
        }

    } catch (Exception e) {
        error "No patch generated: ${e.message}"
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
            // Pentru bloomreach, verifică dacă este authoring sau delivery
            if (stageParams.resourceName.contains("authoring")) {
                resources = valuesContent.authoring.hpa
                if (!resources && valuesContent.bloomreach?.authoring?.hpa) {
                    // Verifică structura alternativă pentru authoring
                    resources = valuesContent.bloomreach.authoring.hpa
                }
                if (!resources) {
                    echo "No HPA configuration found for authoring - skipping patch"
                    return null
                }
            } else {
                resources = valuesContent.delivery.hpa
                if (!resources && valuesContent.bloomreach?.delivery?.hpa) {
                    // Verifică structura alternativă pentru delivery
                    resources = valuesContent.bloomreach.delivery.hpa
                }
                if (!resources) {
                    echo "No HPA configuration found for delivery - skipping patch"
                    return null
                }
            }
        } else {
            // Pentru alte servicii
            resources = valuesContent.hpa
            if (!resources) {
                error "No HPA resources defined in values file for ${stageParams.resourceName}"
            }
        }

        // Construiește JSON-ul în funcție de configurația disponibilă
        def hpaSpec = [
            maxReplicas: resources.pods.max,
            minReplicas: resources.pods.min
        ]

        // Adaugă metrics doar dacă există configurație pentru CPU
        if (resources.metrics?.cpu) {
            hpaSpec.metrics = [
                [
                    type: "Resource",
                    resource: [
                        name: "cpu",
                        target: [
                            type: "Utilization",
                            averageUtilization: resources.metrics.cpu
                        ]
                    ]
                ]
            ]
        }

        def jsonString = """
            {
                "spec": ${groovy.json.JsonOutput.toJson(hpaSpec)}
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

