package com.example.emos.wx.config;

import io.swagger.annotations.ApiOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.ApiSelectorBuilder;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {
    @Bean
    public Docket createRestApi() {
        Docket docket = new Docket(DocumentationType.SWAGGER_2);
        // configure basic info in docket
        ApiInfoBuilder builder = new ApiInfoBuilder();
        builder.title("EMOS在线办公系统");
        // Encapsulate builder
        ApiInfo info = builder.build();
        // introduce builder to docket
        docket.apiInfo(info);

        // specific package->class->methods into docket
        ApiSelectorBuilder selectorBuilder = docket.select();
        // all packages -> all classes
        selectorBuilder.paths(PathSelectors.any());
        // methods with @ApiOperation into docket
        selectorBuilder.apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class));
        // introduce selectorBuilder to docket
        docket = selectorBuilder.build();


        return docket;
    }
}
