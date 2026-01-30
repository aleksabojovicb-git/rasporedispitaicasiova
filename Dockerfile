# Koristimo zvaničnu PHP 8.2 sliku sa Apache web serverom
FROM php:8.2-apache

# 1. Ažuriranje paketa i instalacija potrebnih zavisnosti
# - default-jre: Java Runtime Environment (potrebno za tvoj Java generator rasporeda)
# - libpq-dev: Potrebno za PostgreSQL drajvere
RUN apt-get update && apt-get install -y \
    default-jre \
    libpq-dev \
    && docker-php-ext-install pdo pdo_pgsql

# 2. Kopiranje svih fajlova projekta u kontejner
COPY . /var/www/html/

# 3. Podešavanje Apache DocumentRoot-a na /public folder
# Ovo je važno jer se tvoj index.php i ostali javni fajlovi nalaze u "public" folderu
ENV APACHE_DOCUMENT_ROOT /var/www/html/public
RUN sed -ri -e 's!/var/www/html!${APACHE_DOCUMENT_ROOT}!g' /etc/apache2/sites-available/*.conf
RUN sed -ri -e 's!/var/www/!${APACHE_DOCUMENT_ROOT}!g' /etc/apache2/apache2.conf /etc/apache2/conf-available/*.conf

# 4. Dozvole za upis (Permissions)
# Dajemo vlasništvo nad fajlovima Apache korisniku (www-data) kako bi aplikacija mogla da radi
RUN chown -R www-data:www-data /var/www/html

# 5. Omogući mod_rewrite za .htaccess fajlove (ako ih koristiš za rutiranje)
RUN a2enmod rewrite

# 6. Skripta za pokretanje (Startup script)
# Render.com koristi Environment Variables. Ova skripta uzima te varijable i upisuje ih u .env fajl
# koji tvoja PHP aplikacija može da pročita.
RUN echo "#!/bin/bash" > /start.sh && \
    echo "echo \"DB_HOST=\${DB_HOST}\" > /var/www/html/.env" >> /start.sh && \
    echo "echo \"DB_PORT=\${DB_PORT}\" >> /var/www/html/.env" >> /start.sh && \
    echo "echo \"DB_NAME=\${DB_NAME}\" >> /var/www/html/.env" >> /start.sh && \
    echo "echo \"DB_USER=\${DB_USER}\" >> /var/www/html/.env" >> /start.sh && \
    echo "echo \"DB_PASS=\${DB_PASS}\" >> /var/www/html/.env" >> /start.sh && \
    echo "echo \"DB_SSLMODE=require\" >> /var/www/html/.env" >> /start.sh && \
    echo "echo \"DB_SSLROOTCERT=\" >> /var/www/html/.env" >> /start.sh && \
    echo "apache2-foreground" >> /start.sh && \
    chmod +x /start.sh

# Definišemo komandu koja se izvršava kada se kontejner pokrene
CMD ["/start.sh"]