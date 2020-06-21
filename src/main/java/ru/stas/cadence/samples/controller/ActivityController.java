package ru.stas.cadence.samples.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActivityController {
  @PostMapping("/longoperation")
  private void longOperation() {
    System.out.println("ActivityController. Start long operation");
    try {
      Thread.sleep(60 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
      System.out.println("ActivityController. Interrupted exception");
    }
    System.out.println("ActivityController. Stop long operation");
  }
}
