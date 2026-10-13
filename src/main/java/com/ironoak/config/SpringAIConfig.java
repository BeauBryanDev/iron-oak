package com.ironoak.config;

import com.ironoak.agent.tools.BookingTools;
import com.ironoak.agent.tools.CatalogTools;
import com.ironoak.agent.tools.CNCalculatorTools;
import com.ironoak.agent.tools.EndMillTools;
import com.ironoak.agent.tools.MachineHealthTools;
import com.ironoak.agent.tools.MetalPropertiesTools;
import com.ironoak.agent.tools.PowerToolInfoTool;
import com.ironoak.agent.tools.OrderTools;
import com.ironoak.agent.tools.SearchProductsByCategoryTool;
import com.ironoak.agent.tools.ServiceBookingTool;
import com.ironoak.agent.tools.StockProductLookupTool;
import com.ironoak.agent.tools.SupportTools;
import com.ironoak.agent.tools.VisionTools;
import com.ironoak.agent.tools.WarrantyTools;
import com.ironoak.agent.tools.KnowledgeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application-level Spring AI wiring for Piper. */
@Configuration
public class SpringAIConfig {

    @Bean
    public ChatClient piperChatClient(
            ChatClient.Builder builder,
            com.ironoak.agent.PiperAgentConfig promptConfig,
            CatalogTools catalogTools,
            CNCalculatorTools cnCalculatorTools,
            OrderTools orderTools,
            EndMillTools endMillTools,
            BookingTools bookingTools,
            ServiceBookingTool serviceBookingTool,
            WarrantyTools warrantyTools,
            KnowledgeTools knowledgeTools,
            StockProductLookupTool stockProductLookupTool,
            SearchProductsByCategoryTool searchProductsByCategoryTool,
            VisionTools visionTools,
            MachineHealthTools machineHealthTools,
            MetalPropertiesTools metalPropertiesTools,
            PowerToolInfoTool powerToolInfoTool,
            SupportTools supportTools) {

        return builder.defaultSystem(promptConfig.systemPrompt())
                .defaultTools(catalogTools)
                .defaultTools(cnCalculatorTools)
                .defaultTools(orderTools)
                .defaultTools(endMillTools)
                .defaultTools(bookingTools)
                .defaultTools(serviceBookingTool)
                .defaultTools(warrantyTools)
                .defaultTools(stockProductLookupTool)
                .defaultTools(searchProductsByCategoryTool)
                .defaultTools(visionTools)
                .defaultTools(machineHealthTools)
                .defaultTools(metalPropertiesTools)
                .defaultTools(powerToolInfoTool)
                .defaultTools(knowledgeTools)
                .defaultTools(supportTools)
                .build();
    }
}
