package com.ironoak.agent.tools;

import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.CNCalculator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Validated Piper adapter for the CNC milling engineering calculator. */
@Component
public class CNCalculatorTools {

    @Tool(name = "calculate_cnc_parameters", description = "Milling engineering report from the customer's cutting parameters (all 13 required, units as in each parameter). Returns spindle speed, feed rate, material removal rate, chip thickness, forces, cutting power, torque, Taylor tool life, motor load and a SAFE or DANGER OF MOTOR OVERLOAD status. Ask for missing inputs instead of inventing them (get_material_properties gives kc1, mc and vc). Present results as engineering estimates and flag motor-overload warnings.")
    public Map<String, Object> calculateCncParameters(
            @ToolParam(description = "Cutting speed Vc, m/min, > 0.") double cutting_speed,
            @ToolParam(description = "Cutter diameter, mm, > 0.") double diameter_mm,
            @ToolParam(description = "Feed per tooth fz, mm/tooth, > 0.") double feed_per_tooth,
            @ToolParam(description = "Number of teeth, > 0.") int number_of_teeth,
            @ToolParam(description = "Axial depth of cut ap, mm, > 0.") double depth_of_cut_mm,
            @ToolParam(description = "Radial width of cut ae, mm, > 0.") double width_of_cut_mm,
            @ToolParam(description = "Specific cutting force kc1, N/mm², > 0.") double kc1_n_per_mm2,
            @ToolParam(description = "Cutting-force exponent mc, > 0.") double mc,
            @ToolParam(description = "Machine motor power, kW, > 0.") double max_motor_power_kw,
            @ToolParam(description = "Machine efficiency as a decimal, 0 < e <= 1 (0.85 = 85%).") double machine_efficiency,
            @ToolParam(description = "Helix angle, degrees, 0 to < 90.") double helix_angle_degrees,
            @ToolParam(description = "Taylor constant C, m/min, > 0.") double taylor_c,
            @ToolParam(description = "Taylor exponent n, > 0.") double taylor_n) {

        validateFinite(cutting_speed, "cutting_speed");
        validateFinite(diameter_mm, "diameter_mm");
        validateFinite(feed_per_tooth, "feed_per_tooth");
        validateFinite(depth_of_cut_mm, "depth_of_cut_mm");
        validateFinite(width_of_cut_mm, "width_of_cut_mm");
        validateFinite(kc1_n_per_mm2, "kc1_n_per_mm2");
        validateFinite(mc, "mc");
        validateFinite(max_motor_power_kw, "max_motor_power_kw");
        validateFinite(machine_efficiency, "machine_efficiency");
        validateFinite(helix_angle_degrees, "helix_angle_degrees");
        validateFinite(taylor_c, "taylor_c");
        validateFinite(taylor_n, "taylor_n");

        requirePositive(cutting_speed, "cutting_speed");
        requirePositive(diameter_mm, "diameter_mm");
        requirePositive(feed_per_tooth, "feed_per_tooth");

        if (number_of_teeth < 1) {
            throw new BusinessRuleException("number_of_teeth must be at least 1");
        }
        requirePositive(depth_of_cut_mm, "depth_of_cut_mm");
        requirePositive(width_of_cut_mm, "width_of_cut_mm");
        requirePositive(kc1_n_per_mm2, "kc1_n_per_mm2");
        requirePositive(mc, "mc");
        requirePositive(max_motor_power_kw, "max_motor_power_kw");

        if (machine_efficiency <= 0 || machine_efficiency > 1) {
            throw new BusinessRuleException("machine_efficiency must be greater than 0 and no greater than 1");
        }
        if (helix_angle_degrees < 0 || helix_angle_degrees >= 90) {
            throw new BusinessRuleException("helix_angle_degrees must be between 0 and less than 90");
        }
        requirePositive(taylor_c, "taylor_c");
        requirePositive(taylor_n, "taylor_n");

        CNCalculator calculator = new CNCalculator(
                cutting_speed,
                diameter_mm,
                feed_per_tooth,
                number_of_teeth,
                depth_of_cut_mm,
                width_of_cut_mm,
                kc1_n_per_mm2,
                mc,
                max_motor_power_kw,
                machine_efficiency,
                helix_angle_degrees,
                taylor_c,
                taylor_n);

        return calculator.getEngineeringReport();
    }

    private static void validateFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new BusinessRuleException(field + " must be a finite number");
        }
    }

    private static void requirePositive(double value, String field) {
        if (value <= 0) {
            throw new BusinessRuleException(field + " must be greater than 0");
        }
    }
}
