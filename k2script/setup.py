from setuptools import setup

setup(
    name='k2script',
    version='1.0.0',
    author='K2script Team',
    author_email='team@k2scripting.org',
    description='K2script - Enhanced Kotlin 2.x scripting',
    url='https://github.com/k2scripting/k2script',
    license='MIT',
    packages=['wrappers'],
    entry_points={
        'console_scripts': [
            'k2script=wrappers.k2script_py_wrapper:main'
        ]
    },
    package_data={
        'wrappers': ['k2script.jar'] # Assume k2script.jar is copied to wrappers directory
    },
    classifiers=[
        'Programming Language :: Python :: 3',
        'License :: OSI Approved :: MIT License',
        'Operating System :: OS Independent',
    ],
    python_requires='>=3.6',
)
