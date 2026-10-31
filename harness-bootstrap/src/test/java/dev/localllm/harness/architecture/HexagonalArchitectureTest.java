package dev.localllm.harness.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import picocli.CommandLine;

import dev.localllm.harness.infrastructure.in.cli.handlers.CliExceptionHandler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Module boundaries of variant B (hexagonal without DDD), see ADR-001.
 */
@AnalyzeClasses(
    packages = "dev.localllm.harness",
    importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  @ArchTest
  static final ArchRule applicationDoesNotDependOnOuterModules = noClasses()
      .that().resideInAPackage("..application..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "..infrastructure..", "..adapter..", "..bootstrap..");

  @ArchTest
  static final ArchRule applicationIsFreeOfInfrastructureTechnology = noClasses()
      .that().resideInAPackage("..application..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "org.springframework.ai..", "reactor..", "org.jline..", "picocli..",
          "tools.jackson..", "com.fasterxml.jackson..", "jakarta.persistence..");

  @ArchTest
  static final ArchRule applicationUsesSpringOnlyInGeneratedMappers = noClasses()
      .that().resideInAPackage("..application..")
      .and().haveSimpleNameNotEndingWith("MapperImpl")
      .should().dependOnClassesThat().resideInAPackage("org.springframework..");

  @ArchTest
  static final ArchRule applicationServicesAreNotSpringBeans = classes()
      .that().resideInAPackage("..application.service..")
      .should().notBeAnnotatedWith(Service.class)
      .andShould().notBeAnnotatedWith(Component.class);

  @ArchTest
  static final ArchRule portsAreInterfaces = classes()
      .that().resideInAPackage("..application.port..")
      .and().haveSimpleNameNotEndingWith("package-info")
      .should().beInterfaces();

  @ArchTest
  static final ArchRule infrastructureDoesNotDependOnOuterModules = noClasses()
      .that().resideInAPackage("..infrastructure..")
      .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..bootstrap..");

  @ArchTest
  static final ArchRule adaptersDependOnlyOnTheCore = noClasses()
      .that().resideInAPackage("..adapter..")
      .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..bootstrap..");

  @ArchTest
  static final ArchRule adaptersDoNotDependOnEachOther = slices()
      .matching("dev.localllm.harness.adapter.(*)..")
      .should().notDependOnEachOther();

  @ArchTest
  static final ArchRule mappersLiveInMapperPackages = classes()
      .that().areAnnotatedWith(Mapper.class)
      .should().resideInAPackage("..mapper..");

  @ArchTest
  static final ArchRule exceptionHandlersLiveInHandlersPackages = classes()
      .that().implement(CliExceptionHandler.class)
      .or().implement(CommandLine.IExecutionExceptionHandler.class)
      .should().resideInAPackage("..handlers..");

  @ArchTest
  static final ArchRule noFieldInjection = noFields()
      .should().beAnnotatedWith(Autowired.class);

  @ArchTest
  static final ArchRule noJackson2 = noClasses()
      .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson.databind..");
}
