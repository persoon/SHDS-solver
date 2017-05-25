package edu.nmsu.Home;

import edu.nmsu.Home.Devices.Actuator;
import edu.nmsu.Home.Devices.Device;
import edu.nmsu.Home.Devices.Sensor;
import edu.nmsu.Home.LocalSolver.Solver;
import edu.nmsu.Home.Rules.PredictiveModel;
import edu.nmsu.Home.Rules.PredictiveModelFactory;
import edu.nmsu.Home.Rules.RuleType;
import edu.nmsu.Home.Rules.SchedulingRule;
import edu.nmsu.problem.Parameters;
import edu.nmsu.problem.Utilities;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by nandofioretto on 11/4/16.
 */
public class Home {

    private String name;
    private ArrayList<Actuator> actuators;
    private ArrayList<Sensor> sensors;
    private ArrayList<PredictiveModel> predictiveModels;
    private ArrayList<SchedulingRule>  schedulingRules;

    private Solver solver;
    private int hType;

    public Home(String homeName, int hType) {
        name = homeName;
        actuators = new ArrayList<>();
        sensors = new ArrayList<>();
        predictiveModels = new ArrayList<>();
        schedulingRules = new ArrayList<>();
        this.hType = hType;
        readDevices();
        createPredictiveModels();
    }

    public void addRule(SchedulingRule rule) {
        schedulingRules.add(rule);
    }

    public void activatePassiveRules() {
        // Once all the scheduling rules have been read, we activate a "passive rule" R iff there
        // exists an "active rule" which involves devices whose actions influence the property of R.
        //System.out.println("ACTIVATING PASSIVE RULES");
        for (SchedulingRule pr : schedulingRules) {
            if (pr.getType() == RuleType.passive) {
                pr.setActive(false);
                boolean found = false;
                // for all the active rules, take the actuators they affect.
                for (SchedulingRule ar : schedulingRules) {

                    if (ar.getType() == RuleType.passive)
                        continue;

                    for (Actuator actuator : ar.getPredictiveModel().getActuators()) {
                        // if the actuators affect the property of the passive rule
                        if (actuator.getSensorProperties().contains(pr.getProperty())) {
                            //System.out.println(pr.getProperty());
                            pr.setActive(true);
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
            }
        }

    }

    public ArrayList<SchedulingRule> getSchedulingRules() {
        return schedulingRules;
    }

    public String getName() {
        return name;
    }

    private void createPredictiveModels() {
        for (Sensor s : sensors) {
            PredictiveModel model = PredictiveModelFactory.create(s, actuators, name);
            predictiveModels.add(model);
        }

    }

    private void readDevices() {
        try {
            String content = Utilities.readFile(Parameters.getDeviceDictionaryPath());

            JSONArray jArray = new JSONArray(content.trim());
            JSONObject jDevices = jArray.getJSONObject(hType);

            Iterator<?> keys = jDevices.keys();

            while( keys.hasNext() ) {
                String key = (String)keys.next();
                if ( jDevices.get(key) instanceof JSONObject ) {
                    JSONObject device = jDevices.getJSONObject(key);
                    Device d = Device.create(key, device);

                    if (d instanceof Sensor) {
                        sensors.add((Sensor) d);
                    } else if (d instanceof Actuator) {
                        actuators.add((Actuator) d);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public String toString() {
        String str = " Home: " + name;
        for (Actuator a : actuators) {
            if (a.isActive())
                str += a.toString() + "\n";
        }
        for (Sensor s : sensors) {
            if (s.isActive())
                str += s.toString() + "\n";
        }
        for (PredictiveModel m : predictiveModels) {
            if (m.isActive())
                str += m.toString() + "\n";
        }
        for (SchedulingRule r : schedulingRules) {
            if (r.isActive())
                str += r.toString() + "\n";
        }
        return str;
    }
}
