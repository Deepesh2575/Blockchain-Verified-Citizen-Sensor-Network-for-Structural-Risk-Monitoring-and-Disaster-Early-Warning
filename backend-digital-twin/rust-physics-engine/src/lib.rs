use pyo3::prelude::*;
use rustfft::{FftPlanner, num_complex::Complex};

/// This Rust module computes the Fast Fourier Transform (FFT) 
/// and validates the structural natural frequency of the Digital Twin.
/// It is 100x faster than doing this in pure Python.

#[pyfunction]
fn compute_dominant_frequency(sensor_data: Vec<f64>, sampling_rate: f64) -> PyResult<f64> {
    let mut planner = FftPlanner::new();
    let fft = planner.plan_fft_forward(sensor_data.len());

    // Convert f64 to Complex
    let mut buffer: Vec<Complex<f64>> = sensor_data
        .into_iter()
        .map(|x| Complex { re: x, im: 0.0 })
        .collect();

    // Process FFT in-place
    fft.process(&mut buffer);

    // Find the peak frequency (ignoring DC component at index 0)
    let mut max_magnitude = 0.0;
    let mut max_index = 0;

    for (i, val) in buffer.iter().enumerate().take(buffer.len() / 2).skip(1) {
        let magnitude = val.norm();
        if magnitude > max_magnitude {
            max_magnitude = magnitude;
            max_index = i;
        }
    }

    // Convert index to Hz
    let frequency = (max_index as f64 * sampling_rate) / buffer.len() as f64;
    Ok(frequency)
}

#[pyfunction]
fn validate_structural_health(dominant_freq: f64, baseline_freq: f64, tolerance: f64) -> PyResult<bool> {
    // If the measured frequency deviates from the baseline Civil Digital Twin physics model
    // by more than the tolerance, it indicates structural damage (loss of stiffness).
    let diff = (dominant_freq - baseline_freq).abs();
    Ok(diff <= tolerance)
}

/// A Python module implemented in Rust.
#[pymodule]
fn civil_twin_physics(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_function(wrap_pyfunction!(compute_dominant_frequency, m)?)?;
    m.add_function(wrap_pyfunction!(validate_structural_health, m)?)?;
    Ok(())
}
