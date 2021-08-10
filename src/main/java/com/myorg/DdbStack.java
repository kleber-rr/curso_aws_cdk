package com.myorg;

import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.dynamodb.*;

public class DdbStack extends Stack {

    private final Table productEventsDdb;

    public DdbStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public DdbStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

//configuração apenas para o caso de cobrança por requisição (não precisa do autoscaling)
//        productEventsDdb = Table.Builder.create(this, "ProductEventsDb")
//                .tableName("product-events")
//                .billingMode(BillingMode.PAY_PER_REQUEST) //modo de cobrança
//                .partitionKey(Attribute.builder()
//                        .name("pk")
//                        .type(AttributeType.STRING)
//                        .build())
//                .sortKey(Attribute.builder()
//                        .name("sk")
//                        .type(AttributeType.STRING)
//                        .build())
//                .timeToLiveAttribute("ttl")
//                .removalPolicy(RemovalPolicy.DESTROY) //o ideal em produção é que não se destrua a tabela
//                .build();

        //configuração apenas para o caso de cobrança provisionada
        productEventsDdb = Table.Builder.create(this, "ProductEventsDb")
                .tableName("product-events")
                .billingMode(BillingMode.PROVISIONED) //modo de cobrança
                .readCapacity(1) //capacidade de leitura e escrita
                .writeCapacity(1)

                .partitionKey(Attribute.builder()
                        .name("pk")
                        .type(AttributeType.STRING)
                        .build())

                .sortKey(Attribute.builder()
                        .name("sk")
                        .type(AttributeType.STRING)
                        .build())

                .timeToLiveAttribute("ttl")

                .removalPolicy(RemovalPolicy.DESTROY) //o ideal em produção é que não se destrua a tabela

                .build();

        productEventsDdb.autoScaleReadCapacity(
                EnableScalingProps.builder()
                        .minCapacity(1)
                        .maxCapacity(4)
                        .build())
                .scaleOnUtilization(UtilizationScalingProps.builder()
                        .targetUtilizationPercent(50)
                        .scaleInCooldown(Duration.seconds(10))
                        .scaleOutCooldown(Duration.seconds(10))
                        .build());

        productEventsDdb.autoScaleWriteCapacity(
                        EnableScalingProps.builder()
                                .minCapacity(1)
                                .maxCapacity(4)
                                .build())
                .scaleOnUtilization(UtilizationScalingProps.builder()
                        .targetUtilizationPercent(50)
                        .scaleInCooldown(Duration.seconds(10))
                        .scaleOutCooldown(Duration.seconds(10))
                        .build());
    }

    public Table getProductEventsDdb() {
        return productEventsDdb;
    }
}
