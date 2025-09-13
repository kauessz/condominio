package com.example.condo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
public class DatasourceProbe {
  @Value("${spring.datasource.url:}")      String dsUrl;
  @Value("${spring.datasource.username:}") String dsUser;
  @Value("${spring.datasource.password:}") String dsPass;

  @Value("${spring.flyway.url:}")          String fwUrl;
  @Value("${spring.flyway.user:}")         String fwUser;
  @Value("${spring.flyway.password:}")     String fwPass;

  @PostConstruct
  void log() {
    System.out.println("[DS] url=" + dsUrl);
    System.out.println("[DS] user=" + dsUser);
    System.out.println("[DS] pass.len=" + (dsPass==null?0:dsPass.length()));
    System.out.println("[FW] url=" + fwUrl);
    System.out.println("[FW] user=" + fwUser);
    System.out.println("[FW] pass.len=" + (fwPass==null?0:fwPass.length()));
  }
}