package org.janelia.saalfeldlab;

import java.util.Arrays;

import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.bspline.BSplineLazyCoefficientsInterpolatorFactory;
import net.imglib2.algorithm.interpolation.randomaccess.BSplineInterpolatorFactory;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converters.ClippingConverters;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.LanczosInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.outofbounds.OutOfBoundsConstantValueFactory;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.outofbounds.OutOfBoundsMirrorFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class InterpolationUtil {

	public enum INTERP_OPTIONS { LINEAR, NEAREST, BSPLINE, LANCZOS };

	public static <T extends RealType<T> & NativeType<T>> InterpolatorFactory<T, RandomAccessible<T>> getInterpolator(
			String option, RandomAccessible<T> ra) {
		if (INTERP_OPTIONS.valueOf(option) == INTERP_OPTIONS.LINEAR) {
			return new NLinearInterpolatorFactory<T>();
		} else if (INTERP_OPTIONS.valueOf(option) == INTERP_OPTIONS.NEAREST) {
			return new NearestNeighborInterpolatorFactory<T>();
		} else if (INTERP_OPTIONS.valueOf(option) == INTERP_OPTIONS.BSPLINE) {
			// cubic bspline
			return new BSplineInterpolatorFactory<T>(3);
		} else if (INTERP_OPTIONS.valueOf(option) == INTERP_OPTIONS.LANCZOS) {
			return new LanczosInterpolatorFactory<T>();
		} else
			return null;

	}
	
	public static < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > interpolate( 
			final RandomAccessible<T> img,
			final String interp )
	{
		return Views.interpolate( img, getInterpolator( interp, img ));
	}

	public static < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > interpolateExtend( 
			final RandomAccessibleInterval<T> img,
			final String interp,
			final String extendOption )
	{
		if( INTERP_OPTIONS.valueOf(interp) == INTERP_OPTIONS.BSPLINE )
		{
			int nd = img.numDimensions();
			int[] blockSize = new int[ nd ];
			if( nd == 1 )
				Arrays.fill( blockSize, 256);
			if( nd == 2 )
				Arrays.fill( blockSize, 128);
			else if( nd == 3 )
				Arrays.fill( blockSize, 64);
			else
				Arrays.fill( blockSize, 32);

			final OutOfBoundsFactory<T, RandomAccessibleInterval<T>> oobFactory = outOfBounds( extendOption, Util.getTypeFromInterval(img));

			final DoubleType doubleType = new DoubleType();
			final T outType = Util.getTypeFromInterval( img );

			final BSplineLazyCoefficientsInterpolatorFactory<T, DoubleType> interpFactory = new BSplineLazyCoefficientsInterpolatorFactory<T,DoubleType>(
					img, img, 3, true, doubleType,
					blockSize, oobFactory );

			final RealRandomAccessible<DoubleType> imgInterp = Views.interpolate(img, interpFactory );
			final Converter<DoubleType,T> conv = ClippingConverters.getConverter( doubleType, outType );
			return Converters.convert(imgInterp, conv, outType);
		}
		else
		{
			ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval<T>> imgExt = extend( img, extendOption );
			return Views.interpolate(imgExt, getInterpolator(interp, img));
		}
	}
	
	public static <T extends NumericType<T>> ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval<T>> extend(
			RandomAccessibleInterval<T> img, String option) {

		return Views.extend( img, outOfBounds( option, Util.getTypeFromInterval(img)));
	}

	public static <T extends NumericType<T>> OutOfBoundsFactory<T, RandomAccessibleInterval<T>> outOfBounds(
			final String extendOption, T type) {
		switch (extendOption) {
		case "MIRROR":
			return new OutOfBoundsMirrorFactory<T, RandomAccessibleInterval<T>>(
					OutOfBoundsMirrorFactory.Boundary.SINGLE);
		case "MIRROR2":
			return new OutOfBoundsMirrorFactory<T, RandomAccessibleInterval<T>>(
					OutOfBoundsMirrorFactory.Boundary.DOUBLE);
		case "BORDER":
			return new OutOfBoundsBorderFactory<>();
		case "ZERO":
			T zero = type.copy();
			zero.setZero();
			return new OutOfBoundsConstantValueFactory<>( zero );
		}

		T value = type.copy();
		if (value instanceof IntegerType) {
			int v = Integer.parseInt(extendOption);
			((IntegerType) value).setInteger(v);
		} else if (value instanceof RealType) {
			double v = Double.parseDouble(extendOption);
			((RealType) value).setReal(v);
		}
		return new OutOfBoundsConstantValueFactory<>(value);
	}

}
