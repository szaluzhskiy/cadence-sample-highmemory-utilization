package ru.stas.cadence.samples;

import com.uber.cadence.ClusterInfo;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import lombok.SneakyThrows;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CadenceConnectorApplication implements CommandLineRunner {

    @SneakyThrows
    public static void main(String[] args) {
        SpringApplication.run(CadenceConnectorApplication.class, args);
    }


  @Override
  public void run(String... args) throws Exception {
    IWorkflowService ws = new WorkflowServiceTChannel("localhost", 7933);
    ClusterInfo ci = ws.GetClusterInfo();
    System.out.println(ci);
  }
}
