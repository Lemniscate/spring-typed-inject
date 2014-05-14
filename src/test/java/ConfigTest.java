import com.github.lemniscate.lib.typed.annotation.InjectTyped;
import com.github.lemniscate.lib.typed.processor.InjectTypedAnnotationPostProcessor;
import com.github.lemniscate.lib.typed.processor.ProgramatticConfigurationPostProcessor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.GenericTypeResolver;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;
import java.util.Map;

/**
 * @Author dave 5/11/14 7:59 PM
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = ConfigTest.MultipleConfig.class)
public class ConfigTest {


    @Inject
    private ApplicationContext ctx;

    @InjectTyped("domainClass")
    private FooService<String> stringService;

    @InjectTyped("domainClass")
    private FooService<Long> longService;

    @Test
    public void foo() throws Exception{
        String[] beanNames = ctx.getBeanDefinitionNames();
        Map<String,FooService> impls = ctx.getBeansOfType(FooService.class);
        for(FooService<?> service : impls.values()){
            Class<?>[] types = GenericTypeResolver.resolveTypeArguments(service.getClass(), FooService.class);
            System.out.println(types);
        }

        System.out.println(beanNames);
        System.out.println(stringService.get());
        System.out.println(longService.get());
    }


    @Configuration
    public static class MultipleConfig{
        @Bean
        public BeanDefinitionRegistryPostProcessor programatticConfigurationPostProcessor(){
            return new ProgramatticConfigurationPostProcessor.Builder()
                    .registerGenerated( BaseConfig.class, new Class<?>[]{ String.class } )
                    .registerGenerated( BaseConfig.class, new Class<?>[]{ Long.class } )
                    .registerGenerated( ComplexConfig.class, new Class<?>[]{ Integer.class } )
                    .build();
        }

        @Bean
        public InjectTypedAnnotationPostProcessor injectTypedANnotationPostProcessor(){
            return new InjectTypedAnnotationPostProcessor();
        }
    }

}
