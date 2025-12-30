**How to run this project locally**

Step 1: Download the project
Go to the GitHub page for this project.
Click the green “Code” button and copy the HTTPS link.

Open your terminal and run:

git clone <paste the link here>
cd PokedexTracker


Step 3: Compile the Java server
In the VS Code terminal (or your normal terminal), run:

mkdir out
javac -d out src/Main.java

If this finishes without errors, the server is ready.

Step 4: Start the server
In terminal Run:
java -cp out Main 
You should see a message saying the server is running.

Leave this terminal window open. If you close it, the website will stop.

Step 5: Open the website
Open your browser and go to:

http://localhost:8080

The PokédexTracker website should now appear.








**How to use the site**

Use the tabs at the top to switch between games.
Use the Normal and Shiny buttons to switch tracking modes.
Click a Pokémon to mark it as caught.
Progress bars update automatically.
The Overall Dex updates based on everything you’ve checked.

Stopping the server

When you’re done using the site, go back to the terminal and press:

Ctrl + C
If you have selected anything some data will be modified into a json file (ignore this new file changing it could cause unwanted behavior)
