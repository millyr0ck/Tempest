package rip.tempest.utils.misc;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MovementFix {
    OFF("Off"),
    NORMAL("Normal"),
    TRADITIONAL("Traditional"),
    BACKWARDS_SPRINT("Backwards Sprint");

    final String name;

    @Override
    public String toString() {
        return name;
    }
}
