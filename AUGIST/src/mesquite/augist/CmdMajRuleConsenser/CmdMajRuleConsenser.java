package mesquite.augist.CmdMajRuleConsenser;

import mesquite.lib.*;
import mesquite.lib.duties.*;
import mesquite.augist.lib.CmdTreeConsenser;
import mesquite.consensus.lib.*;

/* ======================================================================== */
/** A slightly altered version of MajRuleTree for additional employer control.*/
public class CmdMajRuleConsenser extends CmdTreeConsenser{
	double frequencyLimit = 0.5;//TODO: frequency limit seems irrelevant for this module...
	MesquiteBoolean useWeights = new MesquiteBoolean(true);
	/**If dumpTable == true, the bipartition frequency is printed to log.  see afterConsensus() method.*/
	MesquiteBoolean dumpTable = new MesquiteBoolean(false);
	
	public String getName() {
		return "Commandable Majority Rules Consenser";
	}
	public String getExplanation() {
		return "Calculates the majority rules consensus tree, allowing more control by employer module.  For internal use only; use Majority Rules Consensus instead.";
	}
	/*.................................................................................................................*/
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		return super.startJob(arguments, condition, hiredByName);
	}
	/*.................................................................................................................*/
	public BipartitionVector getBipartitions(){
		return bipartitions;
	}
	/*.................................................................................................................*/
	public Snapshot getSnapshot(MesquiteFile file) { 
		Snapshot temp = new Snapshot();
		temp.addLine("toggleUseWeights " + useWeights.toOffOnString());
		temp.addLine("toggleDumpTable " + dumpTable.toOffOnString());
		temp.addLine("setFrequencyLimit " + MesquiteDouble.toString(frequencyLimit));
		return temp;
	}
	/*.................................................................................................................*/
	public Object doCommand(String commandName, String arguments, CommandChecker checker){
		if(checker.compare(this.getClass(), "Sets whether to use tree weights for consensus", "[on or off]", commandName, "toggleUseWeights")){
			useWeights.toggleValue(parser.getFirstToken(arguments));
		} 
		else if(checker.compare(this.getClass(), "Sets whether to print bipartition frequencies to log", "[on or off]", commandName, "toggleDumpTable")){
			dumpTable.toggleValue(parser.getFirstToken(arguments));
		}
		else if(checker.compare(this.getClass(), "Sets frequency limit value", "[frequency limit]", commandName, "setFrequencyLimit")){
			MesquiteInteger pos = new MesquiteInteger(0);
			double tempLimit = MesquiteDouble.fromString(arguments, pos);
			if(MesquiteDouble.isCombinable(tempLimit)){
				if(tempLimit >= 0.5){
					frequencyLimit = tempLimit;
				} else frequencyLimit = 0.5;
			} else frequencyLimit = 0.5;
		} else return super.doCommand(commandName, arguments, checker);
		return null;
	}
	/*.................................................................................................................*/
	public void addTree(Tree t){
		if (t==null)
			return;
		if (useWeights.getValue()) {
			bipartitions.setUseWeights(useWeights.getValue());
			MesquiteDouble md = (MesquiteDouble)((Attachable)t).getAttachment(TreesManager.WEIGHT);
			if (md != null) {
				if (md.isCombinable())
					bipartitions.setWeight(md.getValue());
				else
					bipartitions.setWeight(1.0);
			} else
				bipartitions.setWeight(1.0);
		}
		bipartitions.addTree(t);
	}
	/*.................................................................................................................*/
	public void initialize() {
		if (bipartitions!=null) {
			bipartitions.setMode(BipartitionVector.MAJRULEMODE);
		}
	}
	/*.................................................................................................................*/
 	public void afterConsensus() {
 		if (dumpTable.getValue())
			bipartitions.dump();
 	}
	/*.................................................................................................................*/
	public Tree getConsensus(){
		Tree t = bipartitions.makeTree(getFrequencyLimit());
		afterConsensus();
		return t;
	}
	/*.................................................................................................................*/
	public boolean requestPrimaryChoice(){
		return false;  
	}
	/*.................................................................................................................*/
	public boolean isPrerelease(){
		return true;
	}
	/*.................................................................................................................*/
	public boolean isSubstantive(){
		return false;
	}   	 
	/*.................................................................................................................*/
	public double getFrequencyLimit() {
		return frequencyLimit;
	}
	/*.................................................................................................................*/
	public void setFrequencyLimit(double frequencyLimit) {
		if(this.frequencyLimit != frequencyLimit){
			if(frequencyLimit >= 0.5){
				this.frequencyLimit = frequencyLimit;
				parametersChanged();
			}
			else {
				frequencyLimit=0.5;
				if(!MesquiteThread.isScripting()){
					logln("Required frequency must be above 0.50 (attempted to set it to " + frequencyLimit + ")");
				}
			}
		}
	}
	/*.................................................................................................................*/
	public boolean getUseWeights() {
		return useWeights.getValue();
	}
	/*.................................................................................................................*/
	public void setUseWeights(boolean b) {
		if(useWeights.getValue() != b){
			useWeights.setValue(b);
			parametersChanged();
		}
	}
	/*.................................................................................................................*/
	public boolean getDumpTable(){
		return dumpTable.getValue();
	}
	/*.................................................................................................................*/
	public void setDumpTable(boolean dumpTable){
		this.dumpTable.setValue(dumpTable);
	}

	/*.................................................................................................................*/
	/*  ====  For use with/by XMLUtil  ====  */
	public void processMorePreferences (String tag, String content) {
		if ("useWeights".equalsIgnoreCase(tag))
			useWeights.setFromTrueFalseString(content);
		else if ("frequencyLimit".equalsIgnoreCase(tag))
			frequencyLimit = MesquiteDouble.fromString(content);
		else if ("dumpTable".equalsIgnoreCase(tag))
			dumpTable.setFromTrueFalseString(content);
	}
	/*.................................................................................................................*/
	public String prepareMorePreferencesForXML () {
		StringBuffer buffer = new StringBuffer(200);
		StringUtil.appendXMLTag(buffer, 2, "useWeights", useWeights);  
		StringUtil.appendXMLTag(buffer, 2, "frequencyLimit", frequencyLimit);  
		StringUtil.appendXMLTag(buffer, 2, "dumpTable", dumpTable);  
		return buffer.toString();
	}
}
