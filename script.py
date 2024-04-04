# script.py

def main():
    print_output_as_html("Bonjour depuis un script Python !")

def print_output_as_html(message):
    header = """
    <!DOCTYPE html>
    <html lang="fr">
    <head>
        <meta charset="UTF-8">
        <title>Sortie de Script Python</title>
        <style>
            body {
                font-family: 'Arial', sans-serif;
                background-color: #f4f4f4;
                margin: 0;
                padding: 20px;
                line-height: 1.6;
            }
            .content {
                background: #fff;
                padding: 20px;
                margin-top: 20px;
                border-radius: 8px;
                box-shadow: 0 0 10px rgba(0,0,0,0.1);
            }
        </style>
    </head>
    <body>
    """

    footer = """
        </body>
    </html>
    """

    content = f"""
    <div class="content">
        <h1>{message}</h1>
        <p>Ceci est un exemple de sortie HTML générée par un script Python.</p>
    </div>
    """

    print(header + content + footer)

if __name__ == "__main__":
    main()
