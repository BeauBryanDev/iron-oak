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

    @Tool(name = "calculate_cnc_parameters", description = "Calculate a CNC milling engineering report from the customer's supplied cutting parameters. All inputs are required and must use these units: cutting_speed in metres per minute (m/min), diameter in millimetres (mm), feed_per_tooth in millimetres per tooth (mm/tooth), depth_of_cut and width_of_cut in millimetres, kc1 in N/mm², mc dimensionless, max_motor_power_kw in kW, machine_efficiency as a decimal from 0 to 1, helix_angle_degrees in degrees, Taylor C in m/min, and Taylor n dimensionless. Returns spindle speed (RPM), feed rate (mm/min), material removal rate (cm³/min), chip thickness (mm), tangential and axial force (N), cutting power (kW), spindle torque (N·m), estimated Taylor tool life (minutes), required motor power, motor load percentage, and a SAFE or DANGER OF MOTOR OVERLOAD status. These are engineering estimates based on the supplied inputs, not guarantees. Do not invent missing material or machine values; ask the customer for missing inputs. Explain assumptions, units, and motor-load warnings clearly, and recommend qualified machining review before applying aggressive parameters.")
    public Map<String, Object> calculateCncParameters(
            @ToolParam(description = "Cutting speed Vc in metres per minute (m/min), must be greater than 0.") double cutting_speed,
            @ToolParam(description = "Cutter diameter in millimetres (mm), must be greater than 0.") double diameter_mm,
            @ToolParam(description = "Feed per tooth fz in millimetres per tooth (mm/tooth), must be greater than 0.") double feed_per_tooth,
            @ToolParam(description = "Number of cutter teeth, a positive integer.") int number_of_teeth,
            @ToolParam(description = "Axial depth of cut ap in millimetres (mm), must be greater than 0.") double depth_of_cut_mm,
            @ToolParam(description = "Radial width of cut ae in millimetres (mm), must be greater than 0.") double width_of_cut_mm,
            @ToolParam(description = "Specific cutting-force coefficient kc1 in N/mm², must be greater than 0.") double kc1_n_per_mm2,
            @ToolParam(description = "Specific cutting-force exponent mc, must be greater than 0.") double mc,
            @ToolParam(description = "Maximum machine motor power in kilowatts (kW), must be greater than 0.") double max_motor_power_kw,
            @ToolParam(description = "Machine efficiency as a decimal, for example 0.85 for 85 percent; must be greater than 0 and no greater than 1.") double machine_efficiency,
            @ToolParam(description = "Cutter helix angle in degrees; must be finite and between 0 and less than 90 degrees.") double helix_angle_degrees,
            @ToolParam(description = "Taylor tool-life constant C in m/min, must be greater than 0.") double taylor_c,
            @ToolParam(description = "Taylor tool-life exponent n, must be greater than 0.") double taylor_n) {

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
