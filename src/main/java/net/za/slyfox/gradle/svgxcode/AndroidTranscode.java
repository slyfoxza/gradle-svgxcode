/* ********************************************************************************************** */
/*
 * Copyright 2014 Philip Cronje
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/* ********************************************************************************************** */
package net.za.slyfox.gradle.svgxcode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.Transcoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternSet;

/**
 * @author Philip Cronje
 */
// TODO: Implement org.gradle.api.tasks.util.PatternFilterable as in Copy task
public class AndroidTranscode extends DefaultTask {

	private static final Pattern densityRangePattern = Pattern.compile("^([a-z]+dpi)-([a-z]+dpi)$");
	private static final Pattern fileExtensionPattern = Pattern.compile("\\.\\w+$");

	private List<Density> densities;
	private final PatternSet inputPatternSet = new PatternSet();
	private File outputDirectory;
	private int width;

	/**
	 * Allows specification of a range of target densities in string format. For example, {@code
	 * "mdpi-xhdpi"} indicates that the output densities are medium, high, and extra-high.
	 */
	public AndroidTranscode densities(String densities) {

		Matcher matcher = densityRangePattern.matcher(densities);
		if(!matcher.matches()) {
			// TODO: Fall back to single density output
			throw new RuntimeException();
		}

		Density lowerBound = Density.valueOf(matcher.group(1).toUpperCase(Locale.ENGLISH));
		Density upperBound = Density.valueOf(matcher.group(2).toUpperCase(Locale.ENGLISH));

		int lowerOrdinal = lowerBound.ordinal();
		int upperOrdinal = upperBound.ordinal();
		this.densities = new LinkedList<Density>();
		this.densities.add(lowerBound);
		for(int i = lowerOrdinal + 1; i < upperOrdinal; ++i) {
			Density density = Density.values()[i];
			if(density.equals(Density.TVDPI)) {
				continue;
			}
			this.densities.add(Density.values()[i]);
		}
		this.densities.add(upperBound);
		return this;
	}

	public AndroidTranscode include(String... includes) {

		inputPatternSet.include(includes);
		return this;
	}

	public AndroidTranscode into(Object destination) {

		outputDirectory = getProject().mkdir(destination);
		return this;
	}

	/**
	 * @see #transcode(File, String)
	 */
	@TaskAction
	public void transcode() {

		if(width == 0.0) {
			throw new RuntimeException("Must specify a width");
		}

		getProject().fileTree(getProject().getProjectDir())
				.matching(inputPatternSet)
				.visit(new TranscodeFileVisitor());
	}

	/**
	 * @see #transcode(File, File, Density)
	 */
	public void transcode(File file, String name) throws IOException {

		String outputName = generateOutputName(name);

		List<Density> densities = this.densities;
		if(densities == null) {
			densities = Arrays.asList(Density.MDPI, Density.HDPI, Density.XHDPI);
		}

		for(Density density: densities) {
			File outputDirectory = getProject().mkdir(new File(this.outputDirectory,
					"drawable-" + density.name().toLowerCase(Locale.ENGLISH)));
			transcode(file, new File(outputDirectory, outputName), density);
		}
	}

	public void transcode(File inputFile, File outputFile, Density density) throws IOException {

		float width;
		if(density.equals(Density.MDPI)) {
			width = (float)this.width;
		}
		else {
			width = (float)((double)this.width * density.scale);
		}

		FileInputStream inputStream = null;
		FileOutputStream outputStream = null;
		try {
			inputStream = new FileInputStream(inputFile);
			outputStream = new FileOutputStream(outputFile);
			TranscoderInput input = new TranscoderInput(inputStream);
			TranscoderOutput output = new TranscoderOutput(outputStream);
			Transcoder transcoder = new PNGTranscoder();
			transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width);
			try {
				transcoder.transcode(input, output);
			} catch(TranscoderException e) {
				throw new RuntimeException("Transcoder error encountered", e);
			}
		} finally {
			if(inputStream != null) {
				inputStream.close();
			}
			if(outputStream != null) {
				outputStream.close();
			}
		}
	}

	private String generateOutputName(String name) {

		Matcher matcher = fileExtensionPattern.matcher(name);
		return matcher.find() ? matcher.replaceFirst(".png") : name + ".png";
	}

	@Input
	@Optional
	public List<Density> getDensities() {

		return densities;
	}

	@OutputDirectory
	public File getOutputDirectory() {

		return outputDirectory;
	}

	@InputFiles
	@SkipWhenEmpty
	public FileTree getSources() {

		FileTree sources = getProject().fileTree(getProject().getProjectDir())
				.matching(inputPatternSet);
		return sources;
	}

	@Input
	public int getWidth() {

		return width;
	}

	public void setWidth(int width) {

		this.width = width;
	}

	private enum Density {

		LDPI(0.75),
		MDPI(1.0),
		HDPI(1.5),
		TVDPI(213.0 / 160.0),
		XHDPI(2.0),
		XXHDPI(3.0),
		XXXHDPI(4.0);

		public final double scale;

		private Density(double scale) {

			this.scale = scale;
		}
	}

	private class TranscodeFileVisitor extends EmptyFileVisitor {

		@Override
		public void visitFile(FileVisitDetails fileDetails) {

			try {
				transcode(fileDetails.getFile(), fileDetails.getName());
			} catch(IOException e) {
				throw new RuntimeException("I/O error encountered during transcoding", e);
			}
		}
	}
}
