CREATE TABLE "user" (
  id SERIAL PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL
);

CREATE TABLE picture (
  id SERIAL PRIMARY KEY,
  user_id INT REFERENCES public.user,
  path TEXT NOT NULL
);

CREATE TABLE tagging_provider (
  id SERIAL PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT
);

CREATE TABLE tag (
  id SERIAL PRIMARY KEY,
  picture_id INT REFERENCES picture,
  tagging_provider_id INT REFERENCES tagging_provider,
  value TEXT NOT NULL
);

INSERT INTO tagging_provider(title, description) VALUES
  ('imagga', 'Imagga: Image Recognition API, Computer Vision AI'),
  ('ximilar', 'Ximilar: Image Recognition & Visual Search'),
  ('clarifai', 'Clarifai Computer Vision, NLP & Machine Learning Platform')
