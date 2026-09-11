package omegaai3.diagnostics;

import omegaai3.Options;
import omegaai3.plan.FleetPlanner.Proposal;

/** Omega's control decision, separate from the stock pilot's unobservable internal plan. */
public record DecisionView(String state, String reason, String eligibility, String controller, String assignment, String target, String proposal) {
    public static DecisionView describe(Options options, String exclusion, String activePurpose,
                                        Proposal proposal, String controller, String assignment, String target) {
        return describeText(options, exclusion, activePurpose, proposal == null ? "none" : proposal.reason() + ": " + proposal.detail(),
                controller, assignment, target);
    }
    public DecisionView refreshControl(Options options, String exclusion, String activePurpose, String controller, String assignment) {
        return describeText(options, exclusion, activePurpose, proposal, controller, assignment, target);
    }
    private static DecisionView describeText(Options options, String exclusion, String activePurpose, String proposed,
                                             String controller, String assignment, String target) {
        String eligibility = exclusion.isEmpty() ? "Eligible" : exclusion;
        String state, reason;
        if (!options.enabled()) { state = "DISABLED"; reason = "Omega disabled"; }
        else if (options.coordinate() && !options.conflict().isEmpty()) { state = "BLOCKED"; reason = options.conflict(); }
        else if (!exclusion.isEmpty()) { state = "EXCLUDED"; reason = exclusion; }
        else if (!options.coordinate()) {
            state = "OBSERVE";
            reason = proposed.equals("none") ? "No regrouping proposal" : "Proposal only";
        } else if (!activePurpose.isEmpty()) { state = "OWNED RALLY"; reason = activePurpose; }
        else { state = "NO CHANGE"; reason = proposed.equals("none") ? "No regrouping proposal" : "Proposal not applied"; }
        return new DecisionView(state, reason, eligibility, clean(controller), clean(assignment), clean(target), proposed);
    }
    public String label() {
        String pilot = controller.substring(Math.max(0, controller.lastIndexOf('.') + 1));
        String note = eligibility.equals("Eligible") || eligibility.equals(reason) ? "" : "\n" + eligibility;
        String intent = !proposal.equals("none") && !state.equals("OWNED RALLY") ? "\nProposed: " + proposal : "";
        return "Omega: " + state + "\n" + reason + note + intent + "\nPilot: " + pilot + " | Order: " + assignment + "\nTarget: " + target;
    }
    public String eventKey() { return state + "|" + reason + "|" + eligibility + "|" + controller + "|" + assignment + "|" + proposal; }
    public static String clean(String value) { return value == null ? "unknown" : value.replace('\n', ' ').replace('\r', ' '); }
}
