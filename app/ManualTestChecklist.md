# Manual Test Checklist: TXT File Picker

Due to the constraints of instrumenting the system file picker (SAF) across various Android versions locally without an emulator, please manually verify the following scenarios on a physical device or emulator:

1. **Happy Path:**
   - Click "Select .txt File".
   - Select a valid, UTF-8 `.txt` file.
   - Verify the UI shows a progress spinner.
   - Verify that upon completion, the app navigates to BookDetails and the title/content is correct.
   - Verify restarting the app retains the book and content.

2. **File Size Limit:**
   - Attempt to pick a `.txt` file larger than 20MB.
   - Verify the app shows the error: "File is too large. Maximum supported size is 20MB."

3. **Invalid File Type:**
   - Use the file picker to select a file without `.txt` extension (if the picker allows it) or a `.txt` file disguised with binary content.
   - Verify the error message displays appropriately.

4. **Empty File:**
   - Select an empty 0-byte `.txt` file.
   - Verify the app shows the error: "File is empty."

5. **Cancellation/Exit during picker:**
   - Click "Select .txt File".
   - Press the system back button to dismiss the picker.
   - Verify the UI stays on the "Select" state gracefully.
