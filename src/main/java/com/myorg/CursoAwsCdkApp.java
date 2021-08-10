package com.myorg;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

import java.util.Arrays;

public class CursoAwsCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        VpcStack vpcStack = new VpcStack(app, "Vpc"); //REDE PRIVADA
        ClusterStack clusterStack = new ClusterStack(app, "Cluster", vpcStack.getVpc()); //criação de instâncias do servidor de aplicação
        clusterStack.addDependency(vpcStack); //cluster depende da VPC

        RdsStack rdsStack = new RdsStack(app, "Rds", vpcStack.getVpc()); //banco de dados
        rdsStack.addDependency(vpcStack); //rds depende da VPC

        SnsStack snsStack = new SnsStack(app, "Sns"); //serviço de envio de mensagens de email, conforme os eventos criados

        InvoiceAppStack invoiceAppStack = new InvoiceAppStack(app, "InvoiceApp");

        //aplication load balance, helth check, entre outros para uma aplicação específica
        Service01Stack service01Stack = new Service01Stack(app, "Service01", clusterStack.getCluster(), snsStack.getProductEventsTopic(),
                invoiceAppStack.getBucket(), invoiceAppStack.getS3InvoiceQueue());
        service01Stack.addDependency(clusterStack); // service depende do cluster
        service01Stack.addDependency(rdsStack); // service depende do rds
        service01Stack.addDependency(snsStack); //service depende do sns
        service01Stack.addDependency(invoiceAppStack); //service depende do invoiceapp

        DdbStack ddbStack = new DdbStack(app, "Ddb");

        //aplication load balance, helth check, entre outros para uma aplicação específica
        Service02Stack service02Stack = new Service02Stack(app, "Service02", clusterStack.getCluster(), snsStack.getProductEventsTopic(), ddbStack.getProductEventsDdb());
        service02Stack.addDependency(clusterStack); // service depende do cluster
        service02Stack.addDependency(snsStack); // service depende do sns
        service02Stack.addDependency(ddbStack); // service depende do ddbstack

        app.synth();
    }
}
