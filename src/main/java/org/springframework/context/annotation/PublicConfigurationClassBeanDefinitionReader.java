package org.springframework.context.annotation;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.*;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Basically a copy-paste implementation of {@link ConfigurationClassBeanDefinitionReader} to make it public AND to
 * handle naming beans after their class AND their method.
 *
 * This is my least favorite part of this project and will probably go away soon...
 *
 * @Author dave 5/11/14 9:26 PM
 */
public class PublicConfigurationClassBeanDefinitionReader extends ConfigurationClassBeanDefinitionReader {

    private final BeanDefinitionRegistry registry;

    private final SourceExtractor sourceExtractor;

    private final ProblemReporter problemReporter;

    private final MetadataReaderFactory metadataReaderFactory;

    private final ResourceLoader resourceLoader;

    private final Environment environment;

    private final BeanNameGenerator importBeanNameGenerator;

    private final ConditionEvaluator conditionEvaluator;

    public PublicConfigurationClassBeanDefinitionReader(BeanDefinitionRegistry registry, SourceExtractor sourceExtractor, ProblemReporter problemReporter, MetadataReaderFactory metadataReaderFactory, ResourceLoader resourceLoader, Environment environment, BeanNameGenerator importBeanNameGenerator) {
        super(registry, sourceExtractor, problemReporter, metadataReaderFactory, resourceLoader, environment, importBeanNameGenerator);

        this.registry = registry;
        this.sourceExtractor = sourceExtractor;
        this.problemReporter = problemReporter;
        this.metadataReaderFactory = metadataReaderFactory;
        this.resourceLoader = resourceLoader;
        this.environment = environment;
        this.importBeanNameGenerator = importBeanNameGenerator;
        this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
    }


    /**
     * Read {@code configurationModel}, registering bean definitions
     * with the registry based on its contents.
     */
    public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
        TrackedConditionEvaluator trackedConditionEvaluator = new TrackedConditionEvaluator();
        for (ConfigurationClass configClass : configurationModel) {
            loadBeanDefinitionsForConfigurationClass(configClass, trackedConditionEvaluator);
        }
    }

    /**
     * Read a particular {@link ConfigurationClass}, registering bean definitions
     * for the class itself and all of its {@link Bean} methods.
     */
    private void loadBeanDefinitionsForConfigurationClass(ConfigurationClass configClass,
                                                          TrackedConditionEvaluator trackedConditionEvaluator) {

        if (trackedConditionEvaluator.shouldSkip(configClass)) {
            removeBeanDefinition(configClass);
            return;
        }

        if (configClass.isImported()) {
            registerBeanDefinitionForImportedConfigurationClass(configClass);
        }
        for (BeanMethod beanMethod : configClass.getBeanMethods()) {
            loadBeanDefinitionsForBeanMethod(beanMethod);
        }
        loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
        loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
    }

    private void removeBeanDefinition(ConfigurationClass configClass) {
        String beanName = configClass.getBeanName();
        if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
            this.registry.removeBeanDefinition(beanName);
        }
    }

    /**
     * Register the {@link Configuration} class itself as a bean definition.
     */
    private void registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass configClass) {
        AnnotationMetadata metadata = configClass.getMetadata();
        BeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);
        if (ConfigurationClassUtils.checkConfigurationClassCandidate(configBeanDef, this.metadataReaderFactory)) {
            String configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
            this.registry.registerBeanDefinition(configBeanName, configBeanDef);
            configClass.setBeanName(configBeanName);
//            if (logger.isDebugEnabled()) {
//                logger.debug(String.format("Registered bean definition for imported @Configuration class %s", configBeanName));
//            }
        }
        else {
            this.problemReporter.error(
                    new InvalidConfigurationImportProblem(metadata.getClassName(), configClass.getResource(), metadata));
        }
    }

    /**
     * Read the given {@link BeanMethod}, registering bean definitions
     * with the BeanDefinitionRegistry based on its contents.
     */
    private void loadBeanDefinitionsForBeanMethod(BeanMethod beanMethod) {
        if (this.conditionEvaluator.shouldSkip(beanMethod.getMetadata(), ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN)) {
            return;
        }

        ConfigurationClass configClass = beanMethod.getConfigurationClass();
        MethodMetadata metadata = beanMethod.getMetadata();

        ConfigurationClassBeanDefinition beanDef = new ConfigurationClassBeanDefinition(configClass);
        beanDef.setResource(configClass.getResource());
        beanDef.setSource(this.sourceExtractor.extractSource(metadata, configClass.getResource()));
        if (metadata.isStatic()) {
            // static @Bean method
            beanDef.setBeanClassName(configClass.getMetadata().getClassName());
            beanDef.setFactoryMethodName(metadata.getMethodName());
        }
        else {
            // instance @Bean method
            beanDef.setFactoryBeanName(configClass.getBeanName());
            beanDef.setUniqueFactoryMethodName(metadata.getMethodName());
        }
        beanDef.setAutowireMode(RootBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        beanDef.setAttribute(RequiredAnnotationBeanPostProcessor.SKIP_REQUIRED_CHECK_ATTRIBUTE, Boolean.TRUE);

        // Consider name and any aliases
        AnnotationAttributes bean = AnnotationConfigUtils.attributesFor(metadata, Bean.class);
        List<String> names = new ArrayList<String>(Arrays.asList(bean.getStringArray("name")));
        String beanName = (names.size() > 0 ? names.remove(0) : beanMethod.getMetadata().getMethodName());
        for (String alias : names) {
            this.registry.registerAlias(beanName, alias);
        }

        // Has this effectively been overridden before (e.g. via XML)?
        if (isOverriddenByExistingDefinition(beanMethod, beanName)) {
            return;
        }

        AnnotationConfigUtils.processCommonDefinitionAnnotations(beanDef, metadata);

        Autowire autowire = bean.getEnum("autowire");
        if (autowire.isAutowire()) {
            beanDef.setAutowireMode(autowire.value());
        }

        String initMethodName = bean.getString("initMethod");
        if (StringUtils.hasText(initMethodName)) {
            beanDef.setInitMethodName(initMethodName);
        }

        String destroyMethodName = bean.getString("destroyMethod");
        if (StringUtils.hasText(destroyMethodName)) {
            beanDef.setDestroyMethodName(destroyMethodName);
        }

        // Consider scoping
        ScopedProxyMode proxyMode = ScopedProxyMode.NO;
        AnnotationAttributes scope = AnnotationConfigUtils.attributesFor(metadata, Scope.class);
        if (scope != null) {
            beanDef.setScope(scope.getString("value"));
            proxyMode = scope.getEnum("proxyMode");
            if (proxyMode == ScopedProxyMode.DEFAULT) {
                proxyMode = ScopedProxyMode.NO;
            }
        }

        // Replace the original bean definition with the target one, if necessary
        BeanDefinition beanDefToRegister = beanDef;
        if (proxyMode != ScopedProxyMode.NO) {
            BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(
                    new BeanDefinitionHolder(beanDef, beanName), this.registry, proxyMode == ScopedProxyMode.TARGET_CLASS);
            beanDefToRegister =
                    new ConfigurationClassBeanDefinition((RootBeanDefinition) proxyDef.getBeanDefinition(), configClass);
        }

//        if (logger.isDebugEnabled()) {
//            logger.debug(String.format("Registering bean definition for @Bean method %s.%s()",
//                    configClass.getMetadata().getClassName(), beanName));
//        }

        this.registry.registerBeanDefinition(beanName, beanDefToRegister);
    }

    protected boolean isOverriddenByExistingDefinition(BeanMethod beanMethod, String beanName) {
        if (!this.registry.containsBeanDefinition(beanName)) {
            return false;
        }
        BeanDefinition existingBeanDef = this.registry.getBeanDefinition(beanName);

        // Is the existing bean definition one that was created from a configuration class?
        // -> allow the current bean method to override, since both are at second-pass level.
        // However, if the bean method is an overloaded case on the same configuration class,
        // preserve the existing bean definition.
        if (existingBeanDef instanceof ConfigurationClassBeanDefinition) {
            ConfigurationClassBeanDefinition ccbd = (ConfigurationClassBeanDefinition) existingBeanDef;
            return (ccbd.getMetadata().getClassName().equals(beanMethod.getConfigurationClass().getMetadata().getClassName()));
        }

        // Has the existing bean definition bean marked as a framework-generated bean?
        // -> allow the current bean method to override it, since it is application-level
        if (existingBeanDef.getRole() > BeanDefinition.ROLE_APPLICATION) {
            return false;
        }

        // At this point, it's a top-level override (probably XML), just having been parsed
        // before configuration class processing kicks in...
//        if (logger.isInfoEnabled()) {
//            logger.info(String.format("Skipping bean definition for %s: a definition for bean '%s' " +
//                    "already exists. This top-level bean definition is considered as an override.",
//                    beanMethod, beanName));
//        }
        return true;
    }

    private void loadBeanDefinitionsFromImportedResources(
            Map<String, Class<? extends BeanDefinitionReader>> importedResources) {

        Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<Class<?>, BeanDefinitionReader>();
        for (Map.Entry<String, Class<? extends BeanDefinitionReader>> entry : importedResources.entrySet()) {
            String resource = entry.getKey();
            Class<? extends BeanDefinitionReader> readerClass = entry.getValue();
            if (!readerInstanceCache.containsKey(readerClass)) {
                try {
                    // Instantiate the specified BeanDefinitionReader
                    BeanDefinitionReader readerInstance =
                            readerClass.getConstructor(BeanDefinitionRegistry.class).newInstance(this.registry);
                    // Delegate the current ResourceLoader to it if possible
                    if (readerInstance instanceof AbstractBeanDefinitionReader) {
                        AbstractBeanDefinitionReader abdr = ((AbstractBeanDefinitionReader) readerInstance);
                        abdr.setResourceLoader(this.resourceLoader);
                        abdr.setEnvironment(this.environment);
                    }
                    readerInstanceCache.put(readerClass, readerInstance);
                }
                catch (Exception ex) {
                    throw new IllegalStateException(
                            "Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]");
                }
            }
            BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
            // TODO SPR-6310: qualify relative path locations as done in AbstractContextLoader.modifyLocations
            reader.loadBeanDefinitions(resource);
        }
    }

    private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
        for (Map.Entry<ImportBeanDefinitionRegistrar, AnnotationMetadata> entry : registrars.entrySet()) {
            entry.getKey().registerBeanDefinitions(entry.getValue(), this.registry);
        }
    }


    /**
     * {@link RootBeanDefinition} marker subclass used to signify that a bean definition
     * was created from a configuration class as opposed to any other configuration source.
     * Used in bean overriding cases where it's necessary to determine whether the bean
     * definition was created externally.
     */
    @SuppressWarnings("serial")
    private static class ConfigurationClassBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {

        private final AnnotationMetadata annotationMetadata;

        public ConfigurationClassBeanDefinition(ConfigurationClass configClass) {
            this.annotationMetadata = configClass.getMetadata();
            setLenientConstructorResolution(false);
        }

        public ConfigurationClassBeanDefinition(RootBeanDefinition original, ConfigurationClass configClass) {
            super(original);
            this.annotationMetadata = configClass.getMetadata();
        }

        private ConfigurationClassBeanDefinition(ConfigurationClassBeanDefinition original) {
            super(original);
            this.annotationMetadata = original.annotationMetadata;
        }

        @Override
        public AnnotationMetadata getMetadata() {
            return this.annotationMetadata;
        }

        @Override
        public boolean isFactoryMethod(Method candidate) {
            return (super.isFactoryMethod(candidate) && BeanAnnotationHelper.isBeanAnnotated(candidate));
        }

        @Override
        public ConfigurationClassBeanDefinition cloneBeanDefinition() {
            return new ConfigurationClassBeanDefinition(this);
        }
    }


    /**
     * Configuration classes must be annotated with {@link Configuration @Configuration} or
     * declare at least one {@link Bean @Bean} method.
     */
    private static class InvalidConfigurationImportProblem extends Problem {

        public InvalidConfigurationImportProblem(String className, Resource resource, AnnotationMetadata metadata) {
            super(String.format("%s was @Import'ed but is not annotated with @Configuration " +
                    "nor does it declare any @Bean methods; it does not implement ImportSelector " +
                    "or extend ImportBeanDefinitionRegistrar. Update the class to meet one of these requirements " +
                    "or do not attempt to @Import it.", className), new Location(resource, metadata));
        }
    }


    /**
     * Evaluate {@code @Conditional} annotations, tracking results and taking into
     * account 'imported by'.
     */
    private class TrackedConditionEvaluator {

        private final Map<ConfigurationClass, Boolean> skipped = new HashMap<ConfigurationClass, Boolean>();

        public boolean shouldSkip(ConfigurationClass configClass) {
            Boolean skip = this.skipped.get(configClass);
            if (skip == null) {
                if (configClass.isImported()) {
                    if (shouldSkip(configClass.getImportedBy())) {
                        // The config that imported this one was skipped, therefore we are skipped
                        skip = true;
                    }
                }
                if (skip == null) {
                    skip = conditionEvaluator.shouldSkip(configClass.getMetadata(),
                            ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
                }
                this.skipped.put(configClass, skip);
            }
            return skip;
        }
    }
}
