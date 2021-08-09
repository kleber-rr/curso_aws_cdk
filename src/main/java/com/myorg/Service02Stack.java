package com.myorg;

import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;

import java.util.HashMap;
import java.util.Map;

public class Service02Stack extends Stack {

    //VALORES PROPOSTOS NO CURSO
    /*
    private static final Number CPU = 512;
    private static final Number MEMORY_LIMIT = 1024;
    private static final Number DESIRED_COUNT_INSTANCES = 2;
    private static final Number LISTENER_PORT = 9090;
    private static final Number AUTO_SCALING_MIN = 2;
    private static final Number AUTO_SCALING_MAX = 4;
    private static final Number TARGET_UTIL_PERCENT = 50;
    */
    private static final Number CPU = 256;
    private static final Number MEMORY_LIMIT = 512;
    private static final Number DESIRED_COUNT_INSTANCES = 1;
    private static final Number LISTENER_PORT = 9090;
    private static final Number AUTO_SCALING_MIN = 1;
    private static final Number AUTO_SCALING_MAX = 2;
    private static final Number TARGET_UTIL_PERCENT = 50;


    public Service02Stack(final Construct scope, final String id, Cluster cluster, SnsTopic productEventsTopic, Table productEventDdb) {
        this(scope, id, null, cluster, productEventsTopic, productEventDdb);
    }

    public Service02Stack(final Construct scope, final String id, final StackProps props, Cluster cluster, SnsTopic productEventsTopic, Table productEventDdb) {
        super(scope, id, props);

        //
        Queue productEventsDlq = Queue.Builder.create(this,"ProductEventsDlq")
                .queueName("product-events-dlq")
                .build();

        // fila de mensagens com erro
        DeadLetterQueue deadLetterQueue = DeadLetterQueue.builder()
                .queue(productEventsDlq)
                .maxReceiveCount(3)
                .build();

        Queue productEventsQueue = Queue.Builder.create(this,"ProductEvents")
                .queueName("product-events")
                .deadLetterQueue(deadLetterQueue)
                .build();

        SqsSubscription sqsSubscription = SqsSubscription.Builder.create(productEventsQueue).build();
        productEventsTopic.getTopic().addSubscription(sqsSubscription);

        //definicao de variaveis de ambiente para passar valores para o application.properties
        Map<String,String> envVariables = new HashMap<>();
        envVariables.put("AWS_REGION", "us-east-1");
        envVariables.put("AWS_SQS_QUEUE_PRODUCT_EVENTS_NAME", productEventsQueue.getQueueName());

        //criação do application load balance
        ApplicationLoadBalancedFargateService service02 = ApplicationLoadBalancedFargateService.Builder.create(this, "ALB02")
                .serviceName("service-02")
                .cluster(cluster)
                .cpu(CPU)
                .memoryLimitMiB(MEMORY_LIMIT) //quantidade de memoria para rodar a aplicacao
                .desiredCount(DESIRED_COUNT_INSTANCES) //quantidade de instancias iniciais
                .listenerPort(LISTENER_PORT)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .containerName("aws_project02")
                                .image(ContainerImage.fromRegistry("kleberrr/curso_aws_project02:1.3.1"))
                                .containerPort(LISTENER_PORT)
                                .logDriver(
                                        LogDriver.awsLogs(
                                                AwsLogDriverProps.builder()
                                                    .logGroup(
                                                            LogGroup.Builder.create(this, "Service02LogGroup")
                                                            .logGroupName("Service02")
                                                            .removalPolicy(RemovalPolicy.DESTROY)
                                                            .build()
                                                    )
                                                .streamPrefix("Service02")
                                            .build()))
                                .environment(envVariables)
                                .build())
                .publicLoadBalancer(true)
                .build();

        //criação do target Group
        service02.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                .path("/actuator/health")
                .port(String.valueOf(LISTENER_PORT))
                .healthyHttpCodes("200")
                .build());

//        //criação do scalable task count, define qual a capacidade mínima e maxima da config de auto scaling
        ScalableTaskCount scalableTaskCount = service02.getService().autoScaleTaskCount(EnableScalingProps.builder()
                        .minCapacity(AUTO_SCALING_MIN)
                        .maxCapacity(AUTO_SCALING_MAX)
                .build());

        scalableTaskCount.scaleOnCpuUtilization("Service02AutoScaling", CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(TARGET_UTIL_PERCENT) //se o consumo médio de CPU ultrapassar os 50%...
                        .scaleInCooldown(Duration.seconds(60)) // ... em 60 segundos, cria-se uma nova instancia no limite de 4 instancias
                        .scaleOutCooldown(Duration.seconds(60)) //periodo de análise para destruir as instancias que não estão sendo usadas
                .build());

        // AWS > Identify And Access Management - IAM > Roles (Task Roles from Clusters > Task Definitions > Service)
        productEventsQueue.grantConsumeMessages(service02.getTaskDefinition().getTaskRole());

        //atribuir a permissao de acesso a esta tabela
        productEventDdb.grantReadWriteData(service02.getTaskDefinition().getTaskRole());

    }
}
