// Global variables for aggregated data, Chart instance, and filtering settings
let earningsMap = {}; // { GameName: aggregated TotalMoney }
let esportsChart = null;
let showOthers = false; // false: grouped mode; true: threshold mode
let maxDisplay = 10;    // For grouped mode: number of top entries
let threshold = 0;      // For threshold mode: monetary threshold

/**
 * Format a number as US currency.
 * @param {number} num 
 * @returns {string}
 */
function formatCurrency(num) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(num);
}

/**
 * Computes a new maximum for the threshold slider based on the data.
 * Now returns the overall maximum value.
 * @param {Object} dataMap 
 * @returns {number}
 */
function computeThresholdSliderMax(dataMap) {
  const values = Object.values(dataMap).filter(x => !isNaN(x));
  if (values.length === 0) return 1000;
  return Math.max(...values);
}

/**
 * Parses the CSV text and aggregates TotalMoney per GameName.
 * Expected CSV header:
 * IdNo,TotalMoney,GameName,Genre,PlayerNo,TournamentNo,Top_Country,Top_Country_Earnings,Releaseyear
 * @param {string} text 
 * @returns {Object} Aggregated data map.
 */
function parseCSVData(text) {
  const lines = text.split(/\r?\n/);
  const map = {};
  if (lines.length < 2) return map;
  // Skip header row
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;
    // Handle commas inside quotes
    const tokens = line.split(/,(?=(?:(?:[^"]*"){2})*[^"]*$)/);
    if (tokens.length < 3) continue;
    const totalMoney = parseFloat(tokens[1].trim());
    const gameName = tokens[2].trim().replace(/(^"|"$)/g, '');
    if (gameName && !isNaN(totalMoney)) {
      map[gameName] = (map[gameName] || 0) + totalMoney;
    }
  }
  return map;
}

/**
 * Aggregates earningsMap into a new map.
 * In grouped mode (showOthers === false), only the top maxDisplay entries are shown and the rest are grouped as "Other".
 * In threshold mode (showOthers === true), only entries with earnings >= threshold are returned.
 * @param {Object} dataMap 
 * @returns {Object} Filtered map.
 */
function getFilteredData(dataMap) {
  const entries = Object.entries(dataMap);
  // Sort descending by earnings
  entries.sort((a, b) => b[1] - a[1]);
  if (showOthers) {
    return Object.fromEntries(entries.filter(entry => entry[1] >= threshold));
  } else {
    if (entries.length <= maxDisplay) {
      return Object.fromEntries(entries);
    }
    const topEntries = entries.slice(0, maxDisplay);
    const otherSum = entries.slice(maxDisplay).reduce((sum, entry) => sum + entry[1], 0);
    topEntries.push(['Other', otherSum]);
    return Object.fromEntries(topEntries);
  }
}

/**
 * Updates the summary text with aggregated earnings details.
 */
function updateSummary() {
  const summaryEl = document.getElementById('summaryText');
  const filteredData = getFilteredData(earningsMap);
  const entries = Object.entries(filteredData);
  if (entries.length === 0) {
    summaryEl.textContent = 'No valid data found.';
    return;
  }
  let total = entries.reduce((sum, entry) => sum + entry[1], 0);
  let summary = 'Aggregated Earnings:\n\n';
  entries.forEach(([game, earnings]) => {
    let percent = ((earnings / total) * 100).toFixed(1);
    summary += `${game.padEnd(35)} : ${formatCurrency(earnings)} (${percent}%)\n`;
  });
  summary += `\nTotal Earnings: ${formatCurrency(total)}`;
  summaryEl.textContent = summary;
}

/**
 * Creates or updates the Chart.js doughnut chart.
 * Uses a 20% cutout so that the pie area is larger within the container.
 */
function updateChart() {
  const ctx = document.getElementById('esportsChart').getContext('2d');
  const filteredData = getFilteredData(earningsMap);
  const labels = Object.keys(filteredData);
  const data = Object.values(filteredData);
  
  // New pastel rainbow color palette.
  const backgroundColors = [
    'rgba(255, 179, 186, 0.6)',  // Pastel Pink
    'rgba(255, 223, 186, 0.6)',  // Pastel Orange
    'rgba(255, 255, 186, 0.6)',  // Pastel Yellow
    'rgba(186, 255, 201, 0.6)',  // Pastel Green
    'rgba(186, 225, 255, 0.6)',  // Pastel Blue
    'rgba(201, 186, 255, 0.6)',  // Pastel Purple
    'rgba(255, 186, 229, 0.6)',  // Pastel Magenta
    'rgba(255, 210, 186, 0.6)',  // Pastel Peach
    'rgba(210, 255, 186, 0.6)',  // Pastel Light Green
    'rgba(186, 255, 255, 0.6)'   // Pastel Cyan
  ];

  if (esportsChart) {
    esportsChart.data.labels = labels;
    esportsChart.data.datasets[0].data = data;
    esportsChart.update();
  } else {
    esportsChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: data,
          backgroundColor: backgroundColors,
          borderColor: '#fff',
          borderWidth: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '20%',  // Reduced cutout makes the pie area larger
        plugins: {
          title: {
            display: true,
            text: 'Total Earnings per Esports Title',
            font: { size: 18 }
          },
          legend: {
            position: 'bottom',
            labels: { font: { size: 14 } }
          },
          tooltip: {
            callbacks: {
              label: function(context) {
                const label = context.label || '';
                const value = context.parsed;
                return label + ': ' + formatCurrency(value);
              }
            }
          }
        },
        layout: {
          padding: 0
        }
      }
    });
  }
}

/**
 * Displays an error message.
 * @param {string} msg 
 */
function showError(msg) {
  const errorEl = document.getElementById('errorMessage');
  errorEl.textContent = msg;
  setTimeout(() => { errorEl.textContent = ''; }, 5000);
}

/**
 * Handles CSV file import.
 */
document.getElementById('importBtn').addEventListener('click', () => {
  document.getElementById('fileInput').click();
});

document.getElementById('fileInput').addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (!file) return;
  if (file.type !== 'text/csv' && !file.name.endsWith('.csv')) {
    showError('Please select a valid CSV file.');
    return;
  }
  const reader = new FileReader();
  reader.onload = (evt) => {
    try {
      earningsMap = parseCSVData(evt.target.result);
      if (Object.keys(earningsMap).length === 0) {
        showError('No valid data found in the CSV file.');
        document.getElementById('summaryText').textContent = 'No valid data found.';
      } else {
        // In threshold mode, update slider max and default threshold.
        if (showOthers) {
          const newMax = computeThresholdSliderMax(earningsMap);
          document.getElementById('topNSlider').max = newMax;
          threshold = newMax / 2;
          document.getElementById('topNSlider').value = threshold;
          document.getElementById('topNValue').textContent = formatCurrency(threshold);
          document.getElementById('thresholdInput').value = threshold;
        }
        updateSummary();
        updateChart();
      }
    } catch (error) {
      showError('Error processing file. Please check the file format.');
    }
  };
  reader.onerror = () => {
    showError('An error occurred while reading the file.');
  };
  reader.readAsText(file);
});

/**
 * Exports the aggregated data as a CSV file.
 */
function exportCSV() {
  if (Object.keys(earningsMap).length === 0) {
    showError('No data to export. Please import a CSV file first.');
    return;
  }
  let csvContent = 'GameName,TotalEarnings\n';
  for (let game in earningsMap) {
    csvContent += `${game},${earningsMap[game]}\n`;
  }
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'aggregated_data.csv';
  link.click();
}

/**
 * Exports the chart as an image.
 * @param {string} type - "jpeg" or "png"
 */
function exportChart(type) {
  if (!esportsChart) {
    showError('No chart available to export.');
    return;
  }
  const canvas = document.getElementById('esportsChart');
  const mimeType = type === 'jpeg' ? 'image/jpeg' : 'image/png';
  const imageURL = canvas.toDataURL(mimeType);
  const link = document.createElement('a');
  link.href = imageURL;
  link.download = `esports_chart.${type}`;
  link.click();
}

/**
 * Toggles the export options container visibility.
 */
document.getElementById('exportBtn').addEventListener('click', () => {
  const exportOptions = document.getElementById('exportOptions');
  if (exportOptions.classList.contains('hidden')) {
    exportOptions.classList.remove('hidden');
  } else {
    exportOptions.classList.add('hidden');
  }
});

/**
 * Event listeners for export option buttons.
 */
document.getElementById('exportCSV').addEventListener('click', exportCSV);
document.getElementById('exportJPEG').addEventListener('click', () => exportChart('jpeg'));
document.getElementById('exportPNG').addEventListener('click', () => exportChart('png'));

/**
 * Toggle the visibility of the summary container.
 */
document.getElementById('toggleSummaryBtn').addEventListener('click', () => {
  const summaryContainer = document.getElementById('summaryContainer');
  summaryContainer.style.display = summaryContainer.style.display === 'none' ? 'block' : 'none';
});

/**
 * Toggle filtering mode between grouped (top N) and threshold mode.
 */
document.getElementById('toggleOthersBtn').addEventListener('click', () => {
  showOthers = !showOthers;
  if (showOthers) {
    document.getElementById('toggleOthersBtn').textContent = 'Hide Others';
    document.getElementById('manualThreshold').classList.remove('hidden');
    const newMax = computeThresholdSliderMax(earningsMap);
    document.getElementById('topNSlider').max = newMax;
    threshold = newMax / 2;
    document.getElementById('topNSlider').value = threshold;
    document.getElementById('topNValue').textContent = formatCurrency(threshold);
    document.getElementById('thresholdInput').value = threshold;
  } else {
    document.getElementById('toggleOthersBtn').textContent = 'Show Others';
    document.getElementById('manualThreshold').classList.add('hidden');
    document.getElementById('topNSlider').max = 20;
    document.getElementById('topNSlider').value = 10;
    maxDisplay = 10;
    document.getElementById('topNValue').textContent = maxDisplay;
  }
  updateSummary();
  updateChart();
});

/**
 * Update the filter value when the slider changes.
 * In grouped mode, the slider sets maxDisplay.
 * In threshold mode, the slider sets the monetary threshold.
 */
document.getElementById('topNSlider').addEventListener('input', (e) => {
  if (showOthers) {
    threshold = parseFloat(e.target.value);
    document.getElementById('topNValue').textContent = formatCurrency(threshold);
    document.getElementById('thresholdInput').value = threshold;
  } else {
    maxDisplay = parseInt(e.target.value, 10);
    document.getElementById('topNValue').textContent = maxDisplay;
  }
  updateSummary();
  updateChart();
});

/**
 * Allow manual input of threshold dollars in threshold mode.
 */
document.getElementById('thresholdInput').addEventListener('change', (e) => {
  let val = parseFloat(e.target.value);
  if (isNaN(val) || val < 0) {
    val = 0;
  }
  threshold = val;
  document.getElementById('topNSlider').value = threshold;
  document.getElementById('topNValue').textContent = formatCurrency(threshold);
  updateSummary();
  updateChart();
});
