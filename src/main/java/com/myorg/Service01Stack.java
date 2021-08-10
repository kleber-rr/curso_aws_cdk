package com.myorg;

import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sqs.Queue;

import java.util.HashMap;
import java.util.Map;

public class Service01Stack extends Stack {

    //VALORES PROPOSTOS NO CURSO
    /*
    private static final Number CPU = 512;
    private static final Number MEMORY_LIMIT = 1024;
    private static final Number DESIRED_COUNT_INSTANCES = 2;
    private static final Number LISTENER_PORT = 8080;
    private static final Number AUTO_SCALING_MIN = 2;
    private static final Number AUTO_SCALING_MAX = 4;
    private static final Number TARGET_UTIL_PERCENT = 50;
    */
    private static final Number CPU = 256;
    private static final Number MEMORY_LIMIT = 512;
    private static final Number DESIRED_COUNT_INSTANCES = 1;
    private static final Number LISTENER_PORT = 8080;
    private static final Number AUTO_SCALING_MIN = 1;
    private static final Number AUTO_SCALING_MAX = 2;
    private static final Number TARGET_UTIL_PERCENT = 50;

    public Service01Stack(final Construct scope, final String id, Cluster cluster, SnsTopic productEventsTopic,
                          Bucket invoiceBucket, Queue invoiceQueue) {
        this(scope, id, null, cluster, productEventsTopic, invoiceBucket, invoiceQueue);
    }

    public Service01Stack(final Construct scope, final String id, final StackProps props, Cluster cluster, SnsTopic productEventsTopic,
                          Bucket invoiceBucket, Queue invoiceQueue) {
        super(scope, id, props);

        //definicao de variaveis de ambiente para passar valores para o application.properties
        Map<String,String> envVariables = new HashMap<>();
        envVariables.put("SPRING_DATASOURCE_URL", "jdbc:mariadb://"+Fn.importValue("rds-endpoint")
                + ":3306/aws_project01?createDatabaseIfNotExist=true");
        envVariables.put("SPRING_DATASOURCE_USERNAME", "admin");
        envVariables.put("SPRING_DATASOURCE_PASSWORD", Fn.importValue("rds-password"));
        envVariables.put("AWS_REGION", "us-east-1");
        envVariables.put("AWS_SNS_TOPIC_PRODUCT_EVENTS_ARN", productEventsTopic.getTopic().getTopicArn());

        envVariables.put("AWS_S3_BUCKET_INVOICE_NAME", invoiceBucket.getBucketName());
        envVariables.put("AWS_SQS_QUEUE_INVOICE_EVENTS_NAME", invoiceQueue.getQueueName());

        //criação do application load balance
        ApplicationLoadBalancedFargateService service01 = ApplicationLoadBalancedFargateService.Builder.create(this, "ALB01")
                .serviceName("service-01")
                .cluster(cluster)
                .cpu(CPU)
                .memoryLimitMiB(MEMORY_LIMIT) //quantidade de memoria para rodar a aplicacao
                .desiredCount(DESIRED_COUNT_INSTANCES) //quantidade de instancias iniciais
                .listenerPort(LISTENER_PORT)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .containerName("aws_project01")
                                .image(ContainerImage.fromRegistry("kleberrr/curso_aws_project01:1.7.0"))
                                .containerPort(LISTENER_PORT)
                                .logDriver(
                                        LogDriver.awsLogs(
                                                AwsLogDriverProps.builder()
                                                    .logGroup(
                                                            LogGroup.Builder.create(this, "Service01LogGroup")
                                                            .logGroupName("Service01")
                                                            .removalPolicy(RemovalPolicy.DESTROY)
                                                            .build()
                                                    )
                                                .streamPrefix("Service01")
                                            .build()))
                                .environment(envVariables)
                                .build())
                .publicLoadBalancer(true)
                .build();

        //criação do target Group
        service01.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/actuator/health")
                .port(String.valueOf(LISTENER_PORT))
                .healthyHttpCodes("200")
                .build());

        //criação do scalable task count, define qual a capacidade mínima e maxima da config de auto scaling
        ScalableTaskCount scalableTaskCount = service01.getService().autoScaleTaskCount(EnableScalingProps.builder()
                        .minCapacity(AUTO_SCALING_MIN)
                        .maxCapacity(AUTO_SCALING_MAX)
                .build());

        scalableTaskCount.scaleOnCpuUtilization("Service01AutoScaling", CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(TARGET_UTIL_PERCENT) //se o consumo médio de CPU ultrapassar os 50%...
                        .scaleInCooldown(Duration.seconds(60)) // ... em 60 segundos, cria-se uma nova instancia no limite de 4 instancias
                        .scaleOutCooldown(Duration.seconds(60)) //periodo de análise para destruir as instancias que não estão sendo usadas
                .build());

        productEventsTopic.getTopic().grantPublish(service01.getTaskDefinition().getTaskRole());

        invoiceQueue.grantConsumeMessages(service01.getTaskDefinition().getTaskRole());
        invoiceBucket.grantReadWrite(service01.getTaskDefinition().getTaskRole());
    }
}
