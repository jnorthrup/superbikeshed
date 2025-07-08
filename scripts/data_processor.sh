process_data() {
  # Example processing: simply cat the file (replace with actual processing logic)
  cat "$1"
}

# Process each file in the data directory
for file in scripts/data/input/*.csv; do
  # Extract filename without extension
  filename=$(basename "$file" .csv)
  # Attempt to process file and output to /data/output/
  process_data "$file" > scripts/data/output/"$filename".txt
done
