package org.deeplearning4j.models.classifiers.lstm;

import static org.junit.Assert.*;

import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.UniformDistribution;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.OutputLayer;
import org.deeplearning4j.nn.layers.factory.LayerFactories;
import org.deeplearning4j.nn.layers.recurrent.GravesLSTM;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.nn.params.GravesLSTMParamInitializer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;


public class GravesLSTMTest {
	
	@Test
	public void testLSTMGravesForwardBasic(){
		//Very basic test of forward prop. of LSTM layer with a time series.
		//Essentially make sure it doesn't throw any exceptions, and provides output in the correct shape.
		
		int nIn = 13;
		int nHiddenUnits = 17;
		
		NeuralNetConfiguration conf = new NeuralNetConfiguration.Builder().activationFunction("tanh")
                .layer(new org.deeplearning4j.nn.conf.layers.GravesLSTM())
                .nIn(nIn).nOut(nHiddenUnits).build();
	
		GravesLSTM layer = LayerFactories.getFactory(conf.getLayer()).create(conf);
		
		//Data: has shape [miniBatchSize,nIn,timeSeriesLength];
		//Output/activations has shape [miniBatchsize,nHiddenUnits,timeSeriesLength];
		
		INDArray dataSingleExampleTimeLength1 = Nd4j.ones(1,nIn,1);
		INDArray activations1 = layer.activate(dataSingleExampleTimeLength1);
		assertArrayEquals(activations1.shape(),new int[]{1,nHiddenUnits,1});
		
		INDArray dataMultiExampleLength1 = Nd4j.ones(10,nIn,1);
		INDArray activations2 = layer.activate(dataMultiExampleLength1);
		assertArrayEquals(activations2.shape(),new int[]{10,nHiddenUnits,1});
		
		INDArray dataSingleExampleLength12 = Nd4j.ones(1,nIn,12);
		INDArray activations3 = layer.activate(dataSingleExampleLength12);
		assertArrayEquals(activations3.shape(),new int[]{1,nHiddenUnits,12});
		
		INDArray dataMultiExampleLength15 = Nd4j.ones(10,nIn,15);
		INDArray activations4 = layer.activate(dataMultiExampleLength15);
		assertArrayEquals(activations4.shape(),new int[]{10,nHiddenUnits,15});
	}
	
	@Test
	public void testLSTMGravesBackwardBasic(){
		//Very basic test of backprop for mini-batch + time series
		//Essentially make sure it doesn't throw any exceptions, and provides output in the correct shape. 
		
		testGravesBackwardBasicHelper(13,3,17,10,7);
		testGravesBackwardBasicHelper(13,3,17,1,7);		//Edge case: miniBatchSize = 1
		testGravesBackwardBasicHelper(13,3,17,10,1);	//Edge case: timeSeriesLength = 1
		testGravesBackwardBasicHelper(13,3,17,1,1);		//Edge case: both miniBatchSize = 1 and timeSeriesLength = 1
	}
	
	private static void testGravesBackwardBasicHelper(int nIn, int nOut, int lstmNHiddenUnits, int miniBatchSize, int timeSeriesLength ){
		
		INDArray inputData = Nd4j.ones(miniBatchSize,nIn,timeSeriesLength);
		
		NeuralNetConfiguration conf = new NeuralNetConfiguration.Builder().activationFunction("tanh")
				.weightInit(WeightInit.DISTRIBUTION).dist(new UniformDistribution(0, 1))
                .layer(new org.deeplearning4j.nn.conf.layers.GravesLSTM())
                .nIn(nIn).nOut(lstmNHiddenUnits).build();
		
		GravesLSTM lstm = LayerFactories.getFactory(conf.getLayer()).create(conf);
		//Set input, do a forward pass:
		lstm.activate(inputData);
		
		
		NeuralNetConfiguration confOut = new NeuralNetConfiguration.Builder().activationFunction("tanh")
                .layer(new org.deeplearning4j.nn.conf.layers.OutputLayer())
                .weightInit(WeightInit.DISTRIBUTION).dist(new UniformDistribution(0, 1))
                .lossFunction(LossFunctions.LossFunction.MCXENT)
                .nIn(17).nOut(3).build();
		
		OutputLayer outLayer = LayerFactories.getFactory(confOut.getLayer()).create(confOut);
		
		//Create pseudo-gradient for input to LSTM layer (i.e., as if created by OutputLayer)
		//This should have two elements: bias and weight gradients.
		Gradient gradient = new DefaultGradient();
		INDArray pseudoBiasGradients = Nd4j.ones(miniBatchSize,nOut,timeSeriesLength);
		gradient.gradientForVariable().put(DefaultParamInitializer.BIAS_KEY,pseudoBiasGradients);
		INDArray pseudoWeightGradients = Nd4j.ones(miniBatchSize,lstmNHiddenUnits,nOut,timeSeriesLength);
		gradient.gradientForVariable().put(DefaultParamInitializer.WEIGHT_KEY, pseudoWeightGradients);
		
		INDArray sigmaPrimeZLSTM = Nd4j.ones(miniBatchSize,lstmNHiddenUnits,timeSeriesLength);
		Gradient outGradient = lstm.backwardGradient(sigmaPrimeZLSTM, outLayer, gradient, inputData);
		
		INDArray biasGradient = outGradient.getGradientFor(GravesLSTMParamInitializer.BIAS);
		INDArray inWeightGradient = outGradient.getGradientFor(GravesLSTMParamInitializer.INPUT_WEIGHTS);
		INDArray recurrentWeightGradient = outGradient.getGradientFor(GravesLSTMParamInitializer.RECURRENT_WEIGHTS);
		assertNotNull(biasGradient);
		assertNotNull(inWeightGradient);
		assertNotNull(recurrentWeightGradient);
		
		assertArrayEquals(biasGradient.shape(),new int[]{miniBatchSize,4*lstmNHiddenUnits});
		assertArrayEquals(inWeightGradient.shape(),new int[]{nIn,4*lstmNHiddenUnits});
		assertArrayEquals(recurrentWeightGradient.shape(),new int[]{lstmNHiddenUnits,4*lstmNHiddenUnits+3});
		
		//Check update:
		for( String s : outGradient.gradientForVariable().keySet() ){
			lstm.update(outGradient.getGradientFor(s), s);
		}
	}

}
