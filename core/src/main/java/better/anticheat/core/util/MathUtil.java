package better.anticheat.core.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * Utility class providing mathematical helper methods for the anticheat system.
 */
@UtilityClass
public class MathUtil {

    public final double EXPANDER = Math.pow(2, 24);

    /**
     * Calculates the Euclidean norm (hypotenuse) of the given double values.
     * This method computes the square root of the sum of squares of the input numbers.
     *
     * @param number the varargs array of double values to compute the norm for
     * @return the Euclidean norm as a double value
     */
    public double hypot(final double... number) {
        double sum = 0.0;

        for (final double v : number) {
            sum += v * v;
        }

        return Math.sqrt(sum);
    }

    /**
     * Gets the number of consecutive values in the history array
     * that are above the given minimum.
     *
     * @param min the minimum value to check
     * @return the number of consecutive values above the minimum
     */
    public int getConsecutiveAboveX(final double min, final double[] array) {
        var count = 0;

        // Iterate from the end of the array to the beginning
        for (int i = array.length - 1; i >= 0; i--) {
            if (array[i] > min) {
                count++;
            } else {
                // Stop counting when we find a value that's not above 7
                break;
            }
        }

        return count;
    }

    /**
     * Calculates the variance of an array of double values.
     *
     * The variance is computed as the average of the squared differences
     * from the mean. This method provides a measure of how much the values
     * in the array deviate from the average value.
     *
     * @param data an array of double values for which the variance is to be calculated
     * @return the variance as a double value
     */
    public double getVariance(final double[] data) {
        int count = 0;

        double sum = 0.0;
        double variance = 0.0;

        final double average;

        for (final double number : data) {
            sum += number;
            ++count;
        }

        average = sum / count;

        for (final double number : data) {
            variance += Math.pow(number - average, 2.0);
        }

        return variance;
    }

    /**
     * Calculates the standard deviation of an array of double values.
     *
     * The standard deviation is a measure of the amount of variation or dispersion
     * in a set of values. This method computes the standard deviation by first
     * calculating the variance using {@link #getVariance(double[])} and then taking
     * the square root of the variance.
     *
     * @param data an array of double values for which the standard deviation is to be calculated
     * @return the standard deviation as a double value
     */
    public double getStandardDeviation(final double[] data) {
        final double variance = getVariance(data);

        return Math.sqrt(variance);
    }

    /**
     * Calculates the skewness of an array of double values.
     *
     * Skewness is a measure of the asymmetry of the probability distribution of a real-valued
     * random variable about its mean. This method uses the Pearson's second coefficient of skewness,
     * which is calculated as 3 times the difference between the mean and median, divided by the variance.
     *
     * @param data an array of double values for which the skewness is to be calculated
     * @return the skewness as a double value, or Double.NaN if the array is empty
     */
    public double getSkewness(double[] data) {
        if (data.length < 1) return Double.NaN;

        double sum = 0;
        int count = 0;

        final double[] numbers = new double[data.length];

        for (final double number : data) {
            sum += number;

            numbers[count] = number;
            ++count;
        }

        Arrays.sort(numbers);

        final double mean = sum / count;
        final double median = (count % 2 != 0) ? numbers[count / 2] : (numbers[(count - 1) / 2] + numbers[count / 2]) / 2;
        final double variance = getVariance(data);

        return 3 * (mean - median) / variance;
    }

    public double getAverage(double[] data) {
        double sum = 0.0;

        for (final double number : data) {
            sum += number;
        }

        return sum / data.length;
    }

    public double getAverage(float[] data) {
        double sum = 0.0;

        for (final double number : data) {
            sum += number;
        }

        return sum / data.length;
    }

    public double getFluctuation(double[] collection) {
        double max = 0;
        double min = Double.MAX_VALUE;
        double sum = 0;

        for (double i : collection) {
            sum += i;
            if (i > max) max = i;
            if (i < min) min = i;
        }

        double average = sum / collection.length;
        double median = max - ((max - min) / 2);
        return (average / 50) / (median / 50);
    }

    public double getOscillation(double[] samples) {
        return highest(samples) - lowest(samples);
    }

    public double lowest(double[] numbers) {
        double lowest = Double.MAX_VALUE;
        int i = 0;

        for (double number : numbers) {
            if (number < lowest) {
                lowest = number;
            }
        }

        return lowest;
    }

    public double highest(double[] numbers) {
        double lowest = 0.0D;

        for (double number : numbers) {
            if (number > lowest) {
                lowest = number;
            }
        }

        return lowest;
    }

    public double highest(Iterable<Double> numbers) {
        double lowest = 0.0D;

        for (double number : numbers) {
            if (number > lowest) {
                lowest = number;
            }
        }

        return lowest;
    }

    public boolean isExponentiallySmall(final Number number) {
        return number.doubleValue() < 1 && Double.toString(number.doubleValue()).contains("E");
    }

    public boolean isExponentiallyLarge(final Number number) {
        return number.doubleValue() > 10000 && Double.toString(number.doubleValue()).contains("E");
    }

    public float getDistanceBetweenAngles(final float angle1, final float angle2) {
        float distance = Math.abs(angle1 - angle2) % 360.0f;
        if (distance > 180.0f) {
            distance = 360.0f - distance;
        }
        return distance;
    }

    public long getGCD(final long current, final long previous) {
        return (previous <= 16384L) ? current : getGCD(previous, current % previous);
    }

    public double getGCD(final double a, final double b) {
        if (a < b) {
            return getGCD(b, a);
        }

        if (Math.abs(b) < 0.001) {
            return a;
        } else {
            return getGCD(b, a - Math.floor(a / b) * b);
        }
    }

    public static double round(double value, int places) {
        try {
            if (places < 0) throw new IllegalArgumentException();

            BigDecimal bd = new BigDecimal(value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            return bd.doubleValue();
        } catch (NumberFormatException e) {
            return value;
        }
    }

    public static float yawTo180F(float flub) {
        if ((flub %= 360.0f) >= 180.0f) {
            flub -= 360.0f;
        }
        if (flub < -180.0f) {
            flub += 360.0f;
        }
        return flub;
    }

    /**
     * Idfk what this does but GladUrBad's research said this was important so here it is
     * https://github.com/infinitesm/AimAware/blob/main/aimaware-spigot/src/main/java/ai/aimaware/data/feature/FeatureExtractor.java#L124
     */
    public static double getEnergy(double[] values) {
        if (values.length == 0) return 0.0;
        double sumSquares = 0.0;
        for (double v : values) {
            sumSquares += v * v;
        }
        return sumSquares;
    }

    /**
     * Idfk what this does but GladUrBad's research said this was important so here it is
     * https://github.com/infinitesm/AimAware/blob/main/aimaware-spigot/src/main/java/ai/aimaware/data/feature/FeatureExtractor.java#L75
     */
    public static double autocorr(double[] values, int lag) {
        if (values.length <= lag) return 0.0;

        double mean = getAverage(values);
        double num = 0.0;
        double denom = 0.0;

        for (int i = 0; i < values.length - lag; i++) {
            num += (values[i] - mean) * (values[i] - mean);
        }
        for (double v : values) {
            denom += Math.pow(v - mean, 2);
        }

        return denom == 0.0 ? 0.0 : num / denom;
    }

    /**
     * Counts the number of zero-crossings in a sequence.
     * A zero-crossing occurs when consecutive values have opposite signs.
     * 
     * Idfk what this does but GladUrBad's research said this was important so here it is
     * https://github.com/infinitesm/AimAware/blob/main/aimaware-spigot/src/main/java/ai/aimaware/data/feature/FeatureExtractor.java#L75
     *
     * @param values array of values
     * @return number of zero crossings
     */
    public static int zeroCrossings(double[] values) {
        if (values == null || values.length < 2) return 0;
        int count = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] * values[i] < 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Excess kurtosis (Fisher definition) of an array. Returns 0 for length < 4.
     */
    public static double getKurtosis(double[] data) {
        if (data == null || data.length < 4) return 0.0;
        double mean = getAverage(data);
        double m2 = 0.0, m4 = 0.0;
        for (double v : data) {
            double d = v - mean;
            double d2 = d * d;
            m2 += d2;
            m4 += d2 * d2;
        }
        int n = data.length;
        if (m2 == 0.0) return 0.0;
        return (n * m4) / (m2 * m2) - 3.0;
    }

    /**
     * Shannon entropy over normalized magnitudes of data.
     * Values are converted to abs, normalized to sum=1, then H = -sum p*log(p).
     * Returns 0 if all magnitudes are zero.
     */
    public static double getEntropy(double[] data) {
        if (data == null || data.length == 0) return 0.0;
        double sum = 0.0;
        double[] mags = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            mags[i] = Math.abs(data[i]);
            sum += mags[i];
        }
        if (sum == 0.0) return 0.0;
        double h = 0.0;
        for (double m : mags) {
            double p = m / sum;
            if (p > 0.0) {
                h -= p * Math.log(p);
            }
        }
        return h;
    }
}
