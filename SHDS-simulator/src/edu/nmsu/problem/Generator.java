package edu.nmsu.problem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates SHDS problems
 */
public class Generator {

    private final int timeHorizon = Parameters.getHorizon();
    private final double[] priceSchema = Parameters.getPriceSchema();
    int nDevices;

    RuleGenerator ruleGenerator;
    Topology topology;

    public Generator(Topology topology, RuleGenerator ruleGenerator, int nDevices) {
        this.topology = topology;
        this.ruleGenerator = ruleGenerator;
        this.nDevices = nDevices;
    }


    private double[] generateBackgroundLoad() {
        double[] bg = new double[timeHorizon];
        for (int i = 0; i < timeHorizon; i++) {
            bg[i] = ThreadLocalRandom.current().nextDouble(0, 0.3);
        }
        return bg;
    }

    // todo: make version where all agents are constrained with all other agents
    public JSONObject generate() {

        // All agents within a cluster share a constraint
        JSONObject jExperiment = new JSONObject();

        try {
            jExperiment.put("horizon", timeHorizon);
            jExperiment.put("priceSchema", priceSchema);
            //jExperiment.put("agents", );

            JSONObject jAgents = new JSONObject();
            for (String agtName : topology.getAgents()) {
                JSONObject jAgent = new JSONObject();

                // Create array of neighbors
                JSONArray jNeighbors = new JSONArray();

                for (String neigName : topology.getNeighbors(agtName)) {
                    if (neigName.compareTo(agtName) != 0)
                        jNeighbors.put(neigName);
                }

                jAgent.put("neighbors", jNeighbors);
                jAgent.put("backgroundLoad", generateBackgroundLoad());
                jAgent.put("rules", ruleGenerator.generateRules(nDevices));
                jAgents.put(agtName, jAgent);
            }
            jExperiment.put("agents", jAgents);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return jExperiment;
    }




}
