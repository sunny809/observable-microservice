package com.order.demo.infrastructure;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.order.demo")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule application_layer_should_not_depend_on_adapter_or_infrastructure =
            noClasses().that().resideInAPackage("..order.application..")
                    .should().dependOnClassesThat().resideInAnyPackage("..order.adapter..", "..order.infrastructure..");

    @ArchTest
    static final ArchRule inbound_rest_should_not_depend_on_outbound =
            noClasses().that().resideInAPackage("..adapter.inbound..")
                    .should().dependOnClassesThat().resideInAnyPackage("..adapter.outbound..");

    @ArchTest
    static final ArchRule rest_controllers_should_reside_in_adapter_inbound_rest =
            classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should().resideInAPackage("..adapter.inbound.rest..");

    @ArchTest
    static final ArchRule entity_classes_should_reside_in_adapter_outbound =
            classes().that().areAnnotatedWith(Entity.class)
                    .should().resideInAnyPackage("..adapter.outbound.persistence..", "..adapter.outbound.outbox..", "..adapter.outbound.query..", "..adapter.outbound.snapshot..");

    @ArchTest
    static final ArchRule services_should_not_depend_on_jpa_or_webclient =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.jpa..", "org.springframework.web.reactive.function.client..", "jakarta.persistence..", "org.springframework.web.client.." );

    @ArchTest
    static final ArchRule domain_events_should_be_immutable =
            classes().that().resideInAPackage("..domain..")
                    .and().haveSimpleNameEndingWith("Event")
                    .should().haveOnlyFinalFields();

    @ArchTest
    static final ArchRule ports_should_be_interfaces =
            classes().that().resideInAPackage("..port..")
                    .and().haveSimpleNameNotEndingWith("Command")
                    .and().haveSimpleNameNotEndingWith("Request")
                    .and().haveSimpleNameNotEndingWith("Result")
                    .and().haveSimpleNameNotEndingWith("Item")
                    .and().haveSimpleNameNotEndingWith("Ack")
                    .and().haveSimpleNameNotEndingWith("Instruction")
                    .should().beInterfaces();

    @ArchTest
    static final ArchRule no_cyclic_dependencies =
            slices().matching("com.order.demo.(*)..")
                    .should().beFreeOfCycles();
}
