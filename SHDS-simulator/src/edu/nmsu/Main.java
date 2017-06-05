package edu.nmsu;

import edu.nmsu.communication.DCOPagent;
import edu.nmsu.communication.DCOPinfo;
import edu.nmsu.communication.Spawner;
import edu.nmsu.kernel.DCOPInstance;
import edu.nmsu.kernel.DCOPInstanceFactory;
import edu.nmsu.problem.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Main {

    public static int execute(String fileName) {
        CostStat aggStats = new CostStat();
        List<Object> algParams = new ArrayList<>();

        int nbIterations = 1;
        long solverTimeoutMs = 60000;
        double wCost = 1;
        double wPower = 1;
        //Parameters.setHorizon(horizon);

        DCOPInstance dcopInstance = DCOPInstanceFactory.importDCOPInstance(fileName);
        //System.out.println("Horizon: " + Parameters.getHorizon());
        Spawner spawner = new Spawner(dcopInstance);

        algParams.add(nbIterations);
        algParams.add(solverTimeoutMs);
        algParams.add(wCost);
        algParams.add(wPower);

        spawner.spawn(algParams);
        return isSolved(spawner.getSpawnedAgents());
        /*
        CostStat temp = getCostToObject(spawner.getSpawnedAgents());
        aggStats.sumScheduleCost += temp.sumScheduleCost;
        aggStats.sumPowerCost += temp.sumPowerCost;
        aggStats.sumPriceCost += temp.sumPriceCost;
        aggStats.peak += temp.peak;
        aggStats.simTime += temp.simTime;

        return aggStats.simTime + "\t" + aggStats.sumScheduleCost + "\t" + aggStats.sumPriceCost + "\t" + aggStats.sumPowerCost + "\t" + aggStats.peak;
    */
    }

    public static Topology generateTopology(int cID, double gridLength, double clusters) {
        double[] densityCity = {
                718, // 0 --- Des Moines
                1357, // 1 --- Boston
                3766  // 2 --- San Francisco
        };
        if(clusters > (densityCity[cID] * gridLength)/1000) { // This means that there are more clusters than agents which is impossible
            return null;
        }
        return new Topology(densityCity[cID], gridLength, gridLength/clusters);
    }

    public static void main(String[] args) {
/*
        String fileName = "resources/inputs/instance_SF"
                + "_a" + 112
                + "_c" + 1
                + "_d" + 2 + ".json";
        System.out.println(fileName);
        System.out.println(execute(fileName));
*/
        /* 5/23/2017
        String filePath = "resources/inputs/instance_";
        int[] granularity = {60, 45, 40, 30, 15, 10, 5, 2, 1};
        for(int i = 1; i <= 8; i++) {
            String statistics = "";
            for(int g = 0; g < granularity.length; g++) {
                String fileName = filePath + "r" + i + "_s12_g" + granularity[g];
                for(int n = 0; n < 10; n++) {
                    System.out.println(fileName + "_" + n);
                    statistics += fileName+"_"+n+"\n";
                    statistics += execute(fileName + "_" + n + ".json");
                    statistics += "\n";
                }
            }
            try(  PrintWriter pw = new PrintWriter( "resources/outputs/rule_" + i + "_stats.txt" )  ){
                pw.println(statistics);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        */

        int[] granularity = {60, 30, 20, 15, 10, 5, 4, 3, 2, 1};
        String[] rule = {"none", "laundry_wash", "laundry_dry", "dish_wash", "bake", "water_temp", "charge", "temperature_heat", "cleanliness"};
        String filePath = "resources/inputs/";
        int num_files = 30;

        for(int r = 5; r <= 8; r++) {
            String statistics = "";
            for (int tempGran : granularity) {
                System.out.println("granularity: " + tempGran);
                Parameters.setGranularity(tempGran);
                for (int n = 0; n < num_files; n++) {
                    String fileName = "ins_" + r + "_" + n + ".json";
                    int success;
                    System.out.println(fileName);
                    long t1, t2;
                    t1 = System.currentTimeMillis();
                    success = execute(filePath + fileName);
                    t2 = System.currentTimeMillis();
                    statistics += tempGran + "\t" + success + "\t" + (t2 - t1) + "\t" + fileName + "\n";
                }
            }
            writeFile("resources/outputs/" + rule[r] + "_stats.txt", statistics);
        }

    }



    /**
     * Uses PrintWriter to write data to a file. This way we don't have try catch blocks cluttering our code.
     * Parameters:
     *  filePath - the location / file to write to
     *  data - the data that will be written to file
     *  returns true if successful and false if not
     *  "big if true"
    **/
    public static boolean writeFile(String filePath, String data) {
        try (PrintWriter pw = new PrintWriter(filePath)) {
            pw.println(data);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void runTest(int rule_id, int span, int gran) {

        String fileName = "resources/inputs/instance"
                + "_r" + rule_id
                + "_s" + span
                + "_g" + gran + ".json";
        System.out.println(fileName);
        execute(fileName);
    }

    /**
     * returns  1 if solved
     * returns  0 if incorrectly solved
     * returns -1 if bugged
     **/
    public static int isSolved(Collection<DCOPagent> agents) {
        int maxIter = DCOPinfo.leaderAgent.getAgentStatistics().size();
        for (int iter = 0; iter < maxIter; iter++) {
            for (DCOPagent agt : agents) {
                if (iter >= agt.getAgentStatistics().size()) continue;
                double temp = agt.getAgentStatistics().getScheduleCostIter(iter);
                if(temp == 0) {
                    return 0;
                } else if(temp == Double.MAX_VALUE) {
                    return -1;
                }
            }
        }
        return 1;
    }

    public static String getCostToString(Collection<DCOPagent> agents) {
        String res = "";
        /*String res =
                  " simTime\t"
                + " SumSchedCost\t"
                + " SumPriceCost\t"
                + "SumEnergyCost\t"
                + " MaxPowerPeak\n";
*/

        DecimalFormat df = new DecimalFormat("#.00");

        int maxIter = DCOPinfo.leaderAgent.getAgentStatistics().size();
        long simTime = 0; int netLoad = 0;

        double[] aggrPower = new double[Parameters.getHorizon()];

        for (int iter = 0; iter < maxIter; iter++) {
            int iterMsgsSent = 0;
            double sumScheduleCost = 0;
            double sumPriceCost    = 0;
            double sumPowerCost    = 0;
            //double avgGain         = 0;
            //double avgCPtime       = 0;

            for (int i = 0; i < aggrPower.length; i++) aggrPower[i] = 0;

            boolean broken = false;
            for (DCOPagent agt : agents) {
                if (iter >= agt.getAgentStatistics().size()) continue;
                // time
                simTime = Math.max(simTime, agt.getAgentStatistics().getMilliTime(iter));
                //avgCPtime += agt.getAgentStatistics().getSchedulingTimeMsIter(iter);

                // msgs
                int msgNow =  agt.getAgentStatistics().getSentMessages(iter);
                int msgPrev = iter == 0 ? 0 : agt.getAgentStatistics().getSentMessages(iter-1);
                iterMsgsSent += (msgNow - msgPrev);

                // Costs
                // todo: check i think that cost takes into account the power consumption of neighbors -
                sumScheduleCost += agt.getAgentStatistics().getScheduleCostIter(iter);
                for(Double d : agt.getAgentStatistics().getPriceUSDIter(iter)) {
                    if(d == null) {
                        broken = true;
                    }
                }
                if(broken) continue;

                sumPriceCost += Utilities.sum(agt.getAgentStatistics().getPriceUSDIter(iter));
                sumPowerCost += Utilities.sum(agt.getAgentStatistics().getPowerKWhIter(iter));

                //avgGain         += agt.getAgentStatistics().getAgentGainIter(iter);

                // Power
                aggrPower = Utilities.sum(aggrPower, agt.getAgentStatistics().getPowerKWhIter(iter));
            }
            //netLoad += iterMsgsSent;

            double peak = 0;
            for (int i = 0; i < aggrPower.length; i++) {
                peak = Math.max(peak, aggrPower[i]);
            }
            sumPowerCost/=(double) agents.size(); sumPriceCost/=(double) agents.size();
            // time  sched_cost  price  power  peak
            if(!broken) {
                res += df.format(simTime) + "\t "
                        + df.format(sumScheduleCost) + "\t "
                        + df.format(sumPriceCost) + "\t "
                        + df.format(sumPowerCost) + "\t "
                        + df.format(peak) + "\n";
            } else {
                res += "BROKEN\n";
            }


        }
        //System.out.println(res);
        return res;
    }


    public static class CostStat {
        double sumScheduleCost = 0;
        double sumPriceCost    = 0;
        double sumPowerCost    = 0;
        double peak = 0;
        long simTime = 0;
    }

    public static CostStat getCostToObject(Collection<DCOPagent> agents) {
        CostStat stats = new CostStat();

        DecimalFormat df = new DecimalFormat("#.00");

        int maxIter = DCOPinfo.leaderAgent.getAgentStatistics().size();
        long simTime = 0; int netLoad = 0;

        double[] aggrPower = new double[Parameters.getHorizon()];

        for (int iter = 0; iter < maxIter; iter++) {
            int iterMsgsSent = 0;
            double sumScheduleCost = 0;
            double sumPriceCost    = 0;
            double sumPowerCost    = 0;
            //double avgGain         = 0;
            //double avgCPtime       = 0;

            for (int i = 0; i < aggrPower.length; i++) aggrPower[i] = 0;

            boolean broken = false;
            for (DCOPagent agt : agents) {
                if (iter >= agt.getAgentStatistics().size()) continue;

                // time
                simTime = Math.max(simTime, agt.getAgentStatistics().getMilliTime(iter));
                //avgCPtime += agt.getAgentStatistics().getSchedulingTimeMsIter(iter);

                // msgs
                int msgNow =  agt.getAgentStatistics().getSentMessages(iter);
                int msgPrev = iter == 0 ? 0 : agt.getAgentStatistics().getSentMessages(iter-1);
                iterMsgsSent += (msgNow - msgPrev);

                // Costs
                // todo: check i think that cost takes into account the power consumption of neighbors -
                sumScheduleCost += agt.getAgentStatistics().getScheduleCostIter(iter);
                for(Double d : agt.getAgentStatistics().getPriceUSDIter(iter)) {
                    if(d == null) {
                        broken = true;
                    }
                }
                if(broken) continue;

                sumPriceCost += Utilities.sum(agt.getAgentStatistics().getPriceUSDIter(iter));
                sumPowerCost += Utilities.sum(agt.getAgentStatistics().getPowerKWhIter(iter));

                //avgGain         += agt.getAgentStatistics().getAgentGainIter(iter);

                // Power
                aggrPower = Utilities.sum(aggrPower, agt.getAgentStatistics().getPowerKWhIter(iter));
            }
            //netLoad += iterMsgsSent;

            double peak = 0;
            for (int i = 0; i < aggrPower.length; i++) {
                peak = Math.max(peak, aggrPower[i]);
            }
            sumPowerCost/=(double) agents.size();           sumPriceCost/=(double) agents.size();
            sumPowerCost/=(double) Parameters.getHorizon(); sumPriceCost/=(double) Parameters.getHorizon();
            if(!broken) {
                stats.simTime += simTime;
                stats.sumPowerCost += sumPowerCost;
                stats.sumPriceCost += sumPriceCost;
                stats.peak += peak;
                stats.sumScheduleCost += sumScheduleCost;

            } else {
                System.out.println("BROKEN");
                return null;
            }
        }
        return stats;
    }
}
