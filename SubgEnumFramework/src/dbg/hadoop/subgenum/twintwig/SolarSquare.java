package dbg.hadoop.subgenum.twintwig;

import java.io.IOException;
import java.net.URI;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;

import dbg.hadoop.subgenum.frame.GeneralDriver;
import dbg.hadoop.subgraphs.io.HVArray;
import dbg.hadoop.subgraphs.io.HVArrayGroupComparator;
import dbg.hadoop.subgraphs.io.HVArraySign;
import dbg.hadoop.subgraphs.io.HVArraySignComparator;
import dbg.hadoop.subgraphs.io.HyperVertexAdjList;
import dbg.hadoop.subgraphs.utils.BloomFilterOpr;
import dbg.hadoop.subgraphs.utils.Config;
import dbg.hadoop.subgraphs.utils.HyperVertex;
import dbg.hadoop.subgraphs.utils.TwinTwigGenerator;
import dbg.hadoop.subgraphs.utils.Utility;
import dbg.hadoop.subgraphs.utils.InputInfo;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;

@SuppressWarnings("deprecation")
public class SolarSquare{
	private static InputInfo inputInfo = null;

	public static void main(String[] args) throws Exception{
		inputInfo = new InputInfo(args);
		String numReducers = inputInfo.numReducers;
		String inputFilePath = inputInfo.inputFilePath;
		String jarFile = inputInfo.jarFile;
		float falsePositive = inputInfo.falsePositive;
		boolean enableBF = inputInfo.enableBF;
		int maxSize = inputInfo.maxSize;
		String workDir = inputInfo.workDir;
		
		if(inputFilePath.isEmpty()){
			System.err.println("Input file not specified!");
			System.exit(-1);;
		}
		
		if (workDir.toLowerCase().contains("hdfs")) {
			int pos = workDir.substring("hdfs://".length()).indexOf("/")
					+ "hdfs://".length();
			Utility.setDefaultFS(workDir.substring(0, pos));
		} else {
			Utility.setDefaultFS("");
		}
		
		String stageOneOutput = workDir + "tt.solarsquare.tmp.1";
		String stageTwoOutput = workDir + "tt.solarsquare.tmp.2";
		String stageThreeOutput = workDir + "tt.solarsquare.res";
		
		if(Utility.getFS().isDirectory(new Path(stageOneOutput)))
			Utility.getFS().delete(new Path(stageOneOutput));
		if(Utility.getFS().isDirectory(new Path(stageTwoOutput)))
			Utility.getFS().delete(new Path(stageTwoOutput));
		if(Utility.getFS().isDirectory(new Path(stageThreeOutput)))
			Utility.getFS().delete(new Path(stageThreeOutput));
		
		Configuration conf = new Configuration();
		conf.setBoolean("enable.bloom.filter", enableBF);
		if(enableBF){
			conf.setFloat("bloom.filter.false.positive.rate", falsePositive);
			DistributedCache.addCacheFile(new URI(new Path(workDir).toUri()
				.toString() + "/" + Config.bloomFilterFileDir + "/" + "bloomFilter." + 
					Config.TWINTWIG1 + "." + falsePositive), conf);
			
			DistributedCache.addCacheFile(new URI(new Path(workDir).toUri()
				.toString() + "/" + Config.bloomFilterFileDir + "/" + "bloomFilter." + 
					Config.EDGE + "." + falsePositive), conf);
		}
		
		String[] opts1 = { workDir + Config.adjListDir + "." + maxSize, 
				stageOneOutput, numReducers, jarFile };
		ToolRunner.run(conf, new SquareDriver(), opts1);
		
		String[] opts2 = { workDir + Config.adjListDir + "." + maxSize,
				stageOneOutput, stageTwoOutput, numReducers, jarFile };
		
		ToolRunner.run(conf, new GeneralDriver("TwinTwig Solar Square Stage Two", 
				SolarSquareStageTwoMapper1.class,
				SolarSquareStageTwoMapper2.class,
				SolarSquareStageTwoReducer.class, 
				NullWritable.class, HVArray.class, //OutputKV
				HVArraySign.class, HVArray.class, //MapOutputKV
				SequenceFileInputFormat.class, 
				SequenceFileInputFormat.class, 
				SequenceFileOutputFormat.class,
				HVArraySignComparator.class, 
				HVArrayGroupComparator.class), opts2);
		
		String[] opts3 = { workDir + Config.adjListDir + "." + maxSize,
				stageTwoOutput, stageThreeOutput, numReducers, jarFile };
		
		ToolRunner.run(conf, new GeneralDriver("TwinTwig Solar Square Stage Three", 
				SolarSquareStageThreeMapper1.class,
				SolarSquareStageThreeMapper2.class,
				SolarSquareStageThreeReducer.class, 
				NullWritable.class, HVArray.class, //OutputKV
				HVArraySign.class, HVArray.class, //MapOutputKV
				SequenceFileInputFormat.class, 
				SequenceFileInputFormat.class, 
				SequenceFileOutputFormat.class,
				HVArraySignComparator.class, 
				HVArrayGroupComparator.class), opts3);
		
		Utility.getFS().delete(new Path(stageOneOutput));
		Utility.getFS().delete(new Path(stageTwoOutput));
		Utility.getFS().delete(new Path(stageThreeOutput));
	}
}

class SolarSquareStageTwoMapper1 extends
	Mapper<LongWritable, HyperVertexAdjList, HVArraySign, HVArray> {
	
	private TwinTwigGenerator ttwigGen = null;
	@Override
	public void map(LongWritable _key, HyperVertexAdjList _value, Context context) throws IOException, InterruptedException{
		
		ttwigGen = new TwinTwigGenerator(_key.get(), _value);
		ttwigGen.genTwinTwigOne(context, Config.SMALLSIGN, (byte) 3, (byte) 0);
		ttwigGen.genTwinTwigTwo(context, Config.SMALLSIGN, (byte) 3);
		ttwigGen.genTwinTwigThree(context, Config.SMALLSIGN, (byte) 3);
	}
	
	@Override
	public void cleanup(Context context) {
		if (ttwigGen != null) {
			ttwigGen.clear();
			ttwigGen = null;
		}
	}
}

class SolarSquareStageTwoMapper2 extends
		Mapper<NullWritable, HVArray, HVArraySign, HVArray> {
	
	@Override
	public void map(NullWritable _key, HVArray _value, Context context) 
			throws IOException, InterruptedException{
		context.write(new HVArraySign(_value.get(0), _value.get(2), Config.LARGESIGN), 
				new HVArray(_value.get(1), _value.get(3)));
	}
}

class SolarSquareStageTwoReducer extends
	Reducer<HVArraySign, HVArray, NullWritable, HVArray> {
	
	private static TLongArrayList list = null;
	private static boolean enableBF = true;
	private static BloomFilterOpr bloomfilterOpr = null;
	
	@Override
	public void reduce(HVArraySign _key, Iterable<HVArray> values, Context context) 
			throws IOException, InterruptedException{
		if(_key.sign != Config.SMALLSIGN){
			return;
		}
		list.clear();
		long v1 = _key.vertexArray.getFirst();
		long v3 = _key.vertexArray.getSecond();
		boolean isOutput = true;
		for(HVArray val: values){
			if(_key.sign == Config.SMALLSIGN){
				list.add(val.getFirst());
			}
			else{
				TLongIterator iter = list.iterator();
				long v2 = val.getFirst();
				long v4 = val.getSecond();
				while(iter.hasNext()){
					long v0 = iter.next();
					if(enableBF){
						if(v0 < v2) {
							isOutput = bloomfilterOpr.get().test(HyperVertex.VertexID(v0), 
									HyperVertex.VertexID(v2));
						}
						else {
							isOutput = bloomfilterOpr.get().test(HyperVertex.VertexID(v2), 
									HyperVertex.VertexID(v0));
						}
						if(v0 < v4) {
							isOutput = bloomfilterOpr.get().test(HyperVertex.VertexID(v0), 
									HyperVertex.VertexID(v4));
						}
						else {
							isOutput = bloomfilterOpr.get().test(HyperVertex.VertexID(v4), 
									HyperVertex.VertexID(v0));
						}
					}
					if(isOutput) {
						long[] array = { v0, v1, v2, v3, v4 };
						context.write(NullWritable.get(), new HVArray(array));
					}
					
				}
			}
		}
		
	}
	
	@Override
	public void setup(Context context){
		list = new TLongArrayList();
		Configuration conf = context.getConfiguration();
		// We use bloomfilter as static. If it is already loaded, we will have
		// bloomfilterOpr != null, and we donot load it again in the case.
		enableBF = conf.getBoolean("enable.bloom.filter", false);
		if (enableBF && bloomfilterOpr == null) {
			bloomfilterOpr = new BloomFilterOpr(conf.getFloat(
					"bloom.filter.false.positive.rate", (float) 0.001), Config.EDGE);
			try {
				bloomfilterOpr.obtainBloomFilter(conf);
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void cleanup(Context context){
		list.clear();
		list = null;
	}
}

class SolarSquareStageThreeMapper1 extends
		Mapper<LongWritable, HyperVertexAdjList, HVArraySign, HVArray> {

	private TwinTwigGenerator ttwigGen = null;
	private static boolean enableBF = true;
	private static BloomFilterOpr bloomfilterOpr = null;

	@Override
	public void map(LongWritable _key, HyperVertexAdjList _value,
			Context context) throws IOException, InterruptedException {
		
		if(enableBF)
			ttwigGen = new TwinTwigGenerator(_key.get(), _value, bloomfilterOpr.get());
		else
			ttwigGen = new TwinTwigGenerator(_key.get(), _value);
		ttwigGen.genTwinTwigOne(context, Config.SMALLSIGN, (byte) 7, (byte) 0);
		ttwigGen.genTwinTwigTwo(context, Config.SMALLSIGN, (byte) 7);
		ttwigGen.genTwinTwigThree(context, Config.SMALLSIGN, (byte) 7);
	}
	
	@Override
	public void setup(Context context){
		Configuration conf = context.getConfiguration();
		// We use bloomfilter as static. If it is already loaded, we will have
		// bloomfilterOpr != null, and we donot load it again in the case.
		enableBF = conf.getBoolean("enable.bloom.filter", false);
		if (enableBF && bloomfilterOpr == null) {
			bloomfilterOpr = new BloomFilterOpr(conf.getFloat(
					"bloom.filter.false.positive.rate", (float) 0.001), Config.TWINTWIG1);
			try {
				bloomfilterOpr.obtainBloomFilter(conf);
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void cleanup(Context context) {
		if (ttwigGen != null) {
			ttwigGen.clear();
			ttwigGen = null;
		}
	}
}

class SolarSquareStageThreeMapper2 extends
		Mapper<NullWritable, HVArray, HVArraySign, HVArray> {

	@Override
	public void map(NullWritable _key, HVArray _value, Context context)
			throws IOException, InterruptedException {
		context.write(new HVArraySign(_value.get(0), _value.get(2), _value.get(4),
				Config.LARGESIGN), new HVArray(_value.get(1), _value.get(3)));
	}
}

class SolarSquareStageThreeReducer extends
		Reducer<HVArraySign, HVArray, NullWritable, HVArray> {
	@Override
	public void reduce(HVArraySign _key, Iterable<HVArray> values, Context context) 
			throws IOException, InterruptedException{
		if(_key.sign != Config.SMALLSIGN){
			return;
		}
		for(HVArray val: values){
			if(_key.sign == Config.SMALLSIGN){
				continue;
			}
			else {
				long v0 = _key.vertexArray.getFirst();
				long v1 = val.getFirst();
				long v2 = _key.vertexArray.getSecond();
				long v3 = val.getSecond();
				long v4 = _key.vertexArray.getLast();
				long[] array = { v0, v1, v2, v3, v4 };
				context.write(NullWritable.get(), new HVArray(array));
			}
		}
	}
}

