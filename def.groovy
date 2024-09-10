def call(String name = 'User') {
    script {
        setEnvVariables()
        mandatoryVariables()
    }
    pipeline {
        agent { label 'master' }
        stages {
            stage('SK NIGHTLY MICROFRONTEND PIPELINE') {
                steps {
                    script {
                        sk_nightly_microfrontend_pipeline()
                    }
                }
            }

            stage('BUILD') {
                steps {
                    script {
                        build_pipeline()
                    }
                }
            }

            stage('SK BUILD IMAGE') {
                steps {
                    script {
                        if(ondemandbuild == "false" || skovsdeploy == "true") {
                            build_image_fun(product: "SK",
                                            Rel_No: "$release_num",
                                            job_name: "SK/DockerProductRel")
                        }
                    }
                }
            }

            stage('ADD TAG TO LOCAL GIT') {
                steps {
                    script {
                        create_tag_on_local_git()
                    }
                }
            }

            stage('SEND CHANGE LOG') {
                steps {
                    script {
                        try {
                            if (checkfornewcommit == "true") {
                                build job: 'SahiAutomation/ChangeLog',
                                    parameters: [
                                        string(name: 'RELEASE_NAME', value: params["release_num"]),
                                        string(name: 'RELEASE_NUMBER', value: release_number),
                                        string(name: 'IR_NUMBER', value: env.BUILD_ID),
                                        string(name: 'EmailTo', value: params["EmailTo"])
                                    ]
                            }
                        } catch (Exception e) {
                            echo 'SEND CHANGE LOG'
                        }
                    }
                }
            }

            stage('ADD TAG TO SWIFT EASE GIT') {
                steps {
                    script {
                        create_tag_on_swift_ease_git()
                    }
                }
            }

            stage('PULL IN TERRAFORM REPO') {
                steps {
                    script {
                        try {
                            build job: 'pipelines/swiftenterprise/AMI/gitpull',
                                  parameters: [string(name: 'HOST_MACHINE', value: 'executionnode')]
                        } catch (Exception e) {
                            echo 'SEND SWIFT EASE CHANGE LOG'
                        }
                    }
                }
            }

            stage('Deployment & AUTOMATION') {
                parallel {
                    stage('SK OVS DEPLOYMENT') {
                        steps {
                            script {
                                if (ondemandbuild == "false" || skovsdeploy == "true") {
                                    ovs_autodeployment(product: "SK",
                                                       Rel_No: "$release_num",
                                                       Build_No: "${BUILD_ID}",
                                                       PRel_No: "$prev_release_num",
                                                       link: "/view/rNd/job/rNd/job/AutoDeployOnValidationServer")
                                }
                            }
                        }
                    }

                    stage('Update_SKDevOps_Release_Info_Property') {
                        steps {
                            script {
                                if (ondemandbuild == "false") {
                                    build job: 'pipelines/SahiAutomation/Update_SKDevOps_Release_Info_Property',
                                        parameters: [
                                            string(name: 'RELEASE_NAME', value: params["release_num"]),
                                            string(name: 'RELEASE_NUMBER', value: release_number),
                                            string(name: 'HOST_MACHINE', value: 'executionnode'),
                                            string(name: 'IR_NUMBER', value: env.BUILD_ID),
                                            string(name: 'GANDIVA_BRANCH', value: params["gandiva_branch"]),
                                            string(name: 'SWIFT_BRANCH', value: params["swift_branch"])
                                        ]
                                }
                            }
                        }
                    }

                    stage('RUN SONAR SCAN') {
                        steps {
                            script {
                                run_sonar_scan()
                            }
                        }
                    }

                    stage('RUN SK NON-EJB & EJB_JUNIT') {
                        steps {
                            script {
                                run_sk_junit()
                            }
                        }
                    }

                    stage('RUN SK KARATE') {
                        steps {
                            script {
                                run_karate()
                            }
                        }
                    }

                    stage('MOCHA') {
                        steps {
                            script {
                                run_sk_mocha()
                            }
                        }
                    }

                    stage('JMETER') {
                        steps {
                            script {
                                sk_jmeter_automation_run()
                            }
                        }
                    }

                    stage('SK SM EWR') {
                        steps {
                            script {
                                run_SK_SM_EWR_pipeline()
                            }
                        }
                    }

                    stage('SAHI AUTOMATION') {
                        when {
                            expression {
                                return (params.SKQASahiAutomation == "true" || params.SahiFirefox == "true" || params.SahiChrome == "true" || params.SahiEdge == "true" || params.Sahimobile == "true")
                            }
                        }
                        stages {
                            stage('CHROME') {
                                when {
                                    expression {
                                        return params.SahiChrome == "true"
                                    }
                                }
                                steps {
                                    script {
                                        run_sahi_window2_pipeline()
                                    }
                                }
                            }

                            stage('EDGE') {
                                when {
                                    expression {
                                        return params.SahiEdge == "true"
                                    }
                                }
                                steps {
                                    script {
                                        run_sahi_window3_pipeline()
                                    }
                                }
                            }

                            stage('MOBILE AUTOMATION') {
                                when {
                                    expression {
                                        return params.sahimobile == "true"
                                    }
                                }
                                steps {
                                    script {
                                        try {
                                            run_sahi_mobile_pipeline()
                                        } catch (Exception e) {
                                            echo 'MOBILE AUTOMATION FAILED'
                                        }
                                    }
                                }
                            }
                        }
                    }

                    stage('FIREFOX') {
                        when {
                            expression {
                                return params.SahiFirefox == "true"
                            }
                        }
                        steps {
                            script {
                                run_sahi_window1_pipeline()
                            }
                        }

                        stage('SK QA Deployment and Sahi Automation') {
                            steps {
                                script {
                                    if (ondemandbuild == "false" || skqasahiautomation == "true") {
                                        skqa_autodeployment_and_sahi_automation(product: "SK",
                                            HELM: "INSTALL",
                                            RESTORATION: "YES", CDN: "YES",
                                            Rel_Name: "$release_num",
                                            Rel_No: release_number,
                                            PRel_No: "$prev_release_num",
                                            Build_No: env.BUILD_ID,
                                            SCHEDULE_SAHI_RUN: "true",
                                            ENABLE_SAHI_RERUN: "false",
                                            SAHI_HTTPS_URL: "https://nimble.skqa.swiftkanban.com/sk/login.do",
                                            SAHI_HTTPS_DNS: "nimble.skqa.swiftkanban.com",
                                            BROWSER: "chrome",
                                            SAHI_BRANCH: sahi_branch,
                                            gandiva_branch: "$gandiva_branch",
                                            swift_branch: "$swift_branch",
                                            SUITE_NAME: "skqaSanity",
                                            CREATE_MACHINES: "true",
                                            CONFIGURE_EC2: "true",
                                            TERMINATE_MACHINE: "true",
                                            link: "/job/nightly/job/Sk/job/nightly-test")
                                    }
                                }
                            }
                        }
                    }
                }

                stage('SK CONSOLIDATED EMAIL NOTIFICATION') {
                    steps {
                        script {
                            try {
                                if (consolidatedemailnotification == "true") {
                                    build job: 'SahiAutomation/ConsolidatedEmailNotification',
                                          parameters: [
                                            string(name: 'RELEASE_NAME', value: params["release_num"]),
                                            string(name: 'RELEASE_NUMBER', value: release_number),
                                            string(name: 'IR_NUMBER', value: env.BUILD_ID),
                                            string(name: 'EMAIL_TO', value: params["EmailTo"]),
                                            booleanParam(name: 'NewUI', value: newui)
                                          ]
                                }
                            } catch (Exception e) {
                                echo 'CONSOLIDATED EMAIL NOTIFICATION FAILED'
                            }
                        }
                    }
                }
            }
        }
    }
}
