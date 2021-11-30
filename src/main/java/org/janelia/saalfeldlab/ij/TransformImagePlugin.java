package org.janelia.saalfeldlab.ij;

import org.janelia.saalfeldlab.InterpolationUtil;
import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.janelia.utility.parse.ParseUtils;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpService;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.ImgView;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@Plugin(type = Command.class, menuPath = "Plugins>Transforms>Transform Image" )
public class TransformImagePlugin<T extends RealType<T> & NativeType<T>> implements Command {

	@Parameter
	private UIService ui;	

    @Parameter
    private OpService opService;

	@Parameter ( label = "Image to transform" )
	private Dataset image;	

	@Parameter ( label = "Transform H5 file / N5 folder." )
	private String transformUrl;	

	@Parameter ( label = "Output size (comma separated)" )
	private String outputSize;	

	@Parameter ( label = "Output resolution (comma separated)" )
	private String outputResolution;	

	@Parameter( label = "Interpolation",
			choices={"LINEAR", "NEAREST", "BSPLINE", "LANCZOS"},
			style="listBox")
	private String interpolation;

	@Parameter( label="Out-of-bounds option",
			choices={ "ZERO", "BORDER", "MIRROR", "MIRROR2"},
			style="listBox")
	private String outOfBoundsOptions;

	@Override
	public void run() {
		RandomAccessibleInterval<T> img = (RandomAccessibleInterval<T>) image;
		final int nd = img.numDimensions();

		double[] resolution;
		if( outputResolution != null && !outputResolution.isEmpty()) {
			resolution = ParseUtils.parseDoubleArray( outputResolution, "," );
		}
		else {
			resolution = new double[ nd ];
			for( int i = 0; i < nd; i++)
				resolution[i] = image.averageScale(i);
		}

		final Interval renderInterval;
		if( outputSize != null && !outputSize.isEmpty() )
			renderInterval = parseInterval( outputSize );
		else
			renderInterval = img;

		final RealRandomAccessible<T> imgTransformed = transform( img, resolution );
		IntervalView< T > imgXfm = Views.interval(
				Views.raster( imgTransformed ), renderInterval );

		final AxisType[] axes = new AxisType[ nd ];
		axes[0] = Axes.X;
		axes[1] = Axes.Y;
		if( nd > 2 )
			axes[2] = Axes.Z;
		
		ImgPlus<T> imgPlus = new ImgPlus<>(
				ImgView.wrap(imgXfm),
				image.getName() + "-transformed",
				axes, resolution);

		ui.show(imgPlus);
	}	

	public RealRandomAccessible<T> transform( RandomAccessibleInterval<T> img, double[] outputResolution ) {

		final RealRandomAccessible<T> imgInterp = InterpolationUtil.interpolateExtend(img, interpolation, outOfBoundsOptions );
		final RealTransform physicalTransform = TransformReader.read(transformUrl);

		final RealTransform totalTransform;
		if ( outputResolution != null )
		{
			RealTransformSequence tt = new RealTransformSequence();
			tt.add( new Scale( outputResolution ) );
			tt.add( physicalTransform );
			totalTransform = tt;
		}
		else 
			totalTransform = physicalTransform;

		final RealTransformRandomAccessible<T,?> transformedImg = new RealTransformRandomAccessible<>( imgInterp, totalTransform );
		return transformedImg; 
	}	

	public static FinalInterval parseInterval( String outSz )
	{
		FinalInterval destInterval = null;
		if ( outSz.contains( ":" ) )
		{
			String[] minMax = outSz.split( ":" );
			long[] min = ParseUtils.parseLongArray( minMax[ 0 ] );
			long[] max = ParseUtils.parseLongArray( minMax[ 1 ] );
			destInterval = new FinalInterval( min, max );
		} else
		{
			long[] outputSize = ParseUtils.parseLongArray( outSz );
			destInterval = new FinalInterval( outputSize );
		}
		return destInterval;
	}

}
