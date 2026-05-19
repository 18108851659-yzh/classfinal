package net.roseboy.classfinal;

import net.roseboy.classfinal.util.Log;

import java.lang.instrument.Instrumentation;

public class CoreAgent {

    public static void premain(String args, Instrumentation inst) {
        Const.pringInfo();

        AgentTransformer transformer = new AgentTransformer();
        inst.addTransformer(transformer, false);
    }

}
