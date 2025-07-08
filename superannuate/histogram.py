import pandas as pd;
d1names=['SalesNo', 'SalesAreaID', 'date', 'PluNo', 'ItemName', 'Quantity', 'Amount', 'TransMode']
d1=pd.read_fwf('superannuated1909.fwf', 
names=d1names, 
colspecs=[(0, 11), (11, 15), (15, 25), (25, 40), (40, 60), (60, 82), (82, 103), (103, 108)])
for i in d1names: print( (i,len( d1[i].unique() )) )
print (d1['TransMode'].unique())
