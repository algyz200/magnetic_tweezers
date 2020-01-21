#07 01 2015 algirdas.toleikis@gmail.com

#           USAGE
# 1.choose the directory where the file "analysis.xls" is saved.
#is located: click on "R console" then at the top File>Change dir
# 2. Below choose the beads you want to plot. 
#Example: for bead 1 type below: "plotbeads <- c(1)"
#for beads from 1 to 19 but no 3: "plotbeads <- c(1:2,4:19)"
# 4. Choose the files you want to plot at "plotfiles"
# 5. after the first run "analysis.xls" is loaded into the memory
#and the line "myfile <- read.delim("analysis.xls")" can be disabled
#by adding a hash symbol in front
# 6. use "par" function for plotting many graphs in 1 window
#"par(mfrow = c(2,2))" plots 2x2 = 4 graphs. Max is about 25
#depending on monitor resolution.
#For more information on R please see the R manual 
#which is in R folder or search online

############## for the user ########
myfile <- read.delim("analysis.xls")
plotfiles <- c(2)  
plotbeads <-c(2)
par(mfrow = c(1,3))   # number of graphs in 1 window
####################################

library("zoo")
plotfiles <- plotfiles-1
beads <- unique(myfile$Bead)
files <- unique(myfile$File)

n <-20

##bead loop
for (b in plotbeads) {

traces <- list()
	for (d in plotfiles) {
	d <- as.character(d)
	traces[[d]] <- subset(myfile, Bead == b & File == d)       
	}

for (i in plotfiles) {
i <- as.character(i)
label<- as.character(traces[[i]]$Label[[1]]); label<- substr(label,0,11) #label

time <- (traces[[i]])$Time
z <- (traces[[i]])$dz_corrected
#z <- (traces[[i]])$dz

plot(time, z, type = "l", col = "grey", main = label)
lines(time, z, type = "p", col = "grey")
#grid()

##filtering
ztimefilt <- zoo(z, time)
ztimefilt <- aggregate(ztimefilt, identity, mean) #when there are repetitive index
ztimefilt <-rollapply(ztimefilt, n, mean)
ztimefilt <- as.data.frame(ztimefilt)
timefilt <- as.numeric(rownames(ztimefilt)) 
zfilt <- ztimefilt[,1]

##
lines(timefilt, zfilt, type = "l", col = "red", lwd = 3)
}
}##bead loop