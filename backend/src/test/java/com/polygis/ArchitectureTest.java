package com.polygis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import com.polygis.tenancy.SchemaMultiTenantConnectionProvider;
import com.polygis.tenancy.TenantSchemaMigrator;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Guard rails that keep the module boundaries of the starter intact as the code base grows. */
@AnalyzeClasses(packages = "com.polygis", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule tenancyCoreIsFoundational = noClasses().that().resideInAPackage("..tenancy..")
            .should().dependOnClassesThat().resideInAnyPackage("..user..", "..platform..", "..account..",
                    "..settings..", "..audit..", "..mfa..", "..mail..")
            .because("the multi-tenancy core must not know about business modules built on top of it");

    @ArchTest
    static final ArchRule onlyTenancyAndPlatformRunDdl = noClasses().that()
            .resideOutsideOfPackages("..tenancy..", "..platform..")
            .should().dependOnClassesThat().areAssignableTo(TenantSchemaMigrator.class)
            .because("schema creation and migration are provisioning concerns");

    @ArchTest
    static final ArchRule nobodyBypassesTenantConnections = noClasses().that().resideOutsideOfPackage("..tenancy..")
            .should().dependOnClassesThat().areAssignableTo(SchemaMultiTenantConnectionProvider.class)
            .because("connections must be handed out per tenant by Hibernate only");

    @ArchTest
    static final ArchRule platformIsNotUsedByTenantModules = noClasses().that()
            .resideInAnyPackage("..user..", "..account..", "..settings..", "..auth..", "..audit..", "..mfa..")
            .should().dependOnClassesThat().resideInAPackage("..platform..")
            .because("tenant-scoped modules must not reach into super-admin functionality");

    @ArchTest
    static final ArchRule ownershipGoesThroughTheAccessPolicy = noClasses().that().resideOutsideOfPackage("..access..")
            .should().dependOnClassesThat().areAssignableTo(com.polygis.access.ResourceRelationRepository.class)
            .orShould().dependOnClassesThat().areAssignableTo(com.polygis.access.TupleRelationSource.class)
            .because("modules ask AccessPolicy who may do what; they never read or write relations themselves");

    @ArchTest
    static final ArchRule controllersLiveInFeaturePackagesNotConfig = classes().that()
            .areAnnotatedWith(RestController.class).should().resideOutsideOfPackage("..config..");

    @ArchTest
    static final ArchRule constructorInjectionOnly = noFields().should().beAnnotatedWith(Autowired.class)
            .because("constructor injection keeps dependencies explicit and classes testable");
}
