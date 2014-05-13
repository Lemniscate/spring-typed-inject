package com.github.lemniscate.lib.typed.processor;

import com.github.lemniscate.lib.typed.util.JavassistUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.NullSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.PublicConfigurationClassBeanDefinitionReader;
import org.springframework.context.annotation.PublicConfigurationClassParser;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.Assert;

import javax.inject.Inject;
import java.util.*;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ProgramatticConfigurationPostProcessor implements BeanDefinitionRegistryPostProcessor {

    private final List<Class<?>> configs;

    @Inject
    private ApplicationContext ctx;

    @Inject
    private Environment environment;

    private static final JavassistUtil javassistUtil = new JavassistUtil();

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        process(registry);
    }

    private void process(BeanDefinitionRegistry registry){
        PublicConfigurationClassParser parser = new PublicConfigurationClassParser(
                this.metadataReaderFactory, this.problemReporter, this.environment,
                this.resourceLoader, this.componentScanBeanNameGenerator, registry);
        Set<BeanDefinitionHolder> configCandidates = new HashSet<BeanDefinitionHolder>();

        for(Class<?> config : configs){
            registry.registerBeanDefinition( config.getSimpleName(), new RootBeanDefinition(config));
            configCandidates.add(new BeanDefinitionHolder( new RootBeanDefinition(config), config.getSimpleName()));
        }

        parser.parse(configCandidates);
        parser.validate();

        PublicConfigurationClassBeanDefinitionReader reader = new PublicConfigurationClassBeanDefinitionReader(registry, sourceExtractor, problemReporter, metadataReaderFactory,
                resourceLoader, environment, importBeanNameGenerator);

        reader.loadBeanDefinitions(parser.getConfigurationClasses());

        reader.toString();
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {}

    public static class Builder{

        private List<Class<?>> configs = new ArrayList<Class<?>>();

        public Builder registerGenerated(Class<?> config, Class<?>[] types){
            Assert.notNull(config);
            Assert.notEmpty(types);
            Class<?> generated = javassistUtil.createTypedSubclass( config, types);
            configs.add( generated );
            return this;
        }

        public Builder register(Class<?> config){
            Assert.notNull(config);
            configs.add( config );
            return this;
        }

        public ProgramatticConfigurationPostProcessor build(){
            return new ProgramatticConfigurationPostProcessor(configs);
        }
    }

    // ------ Dependency stuff --------

    private final MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();
    private final ProblemReporter problemReporter = new FailFastProblemReporter();
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    private final BeanNameGenerator componentScanBeanNameGenerator = new AnnotationBeanNameGenerator();
    private final SourceExtractor sourceExtractor = new NullSourceExtractor();

    /* using fully qualified class names as default bean names */
    private static final BeanNameGenerator importBeanNameGenerator = new AnnotationBeanNameGenerator() {
        @Override
        protected String buildDefaultBeanName(BeanDefinition definition) {
            return definition.getBeanClassName();
        }
    };
}
